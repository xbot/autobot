#!/usr/bin/python2
# -*- coding: utf-8 -*-

"""
───────────▄▄▄▄▄▄▄▄▄───────────
────────▄█████████████▄────────
█████──█████████████████──█████
▐████▌─▀███▄───────▄███▀─▐████▌
─█████▄──▀███▄───▄███▀──▄█████─
─▐██▀███▄──▀███▄███▀──▄███▀██▌─
──███▄▀███▄──▀███▀──▄███▀▄███──
──▐█▄▀█▄▀███─▄─▀─▄─███▀▄█▀▄█▌──
───███▄▀█▄██─██▄██─██▄█▀▄███───
────▀███▄▀██─█████─██▀▄███▀────
───█▄─▀█████─█████─█████▀─▄█───
───███────────███────────███───
───███▄────▄█─███─█▄────▄███───
───█████─▄███─███─███▄─█████───
───█████─████─███─████─█████───
───█████─████─███─████─█████───
───█████─████─███─████─█████───
───█████─████▄▄▄▄▄████─█████───
────▀███─█████████████─███▀────
──────▀█─███─▄▄▄▄▄─███─█▀──────
─────────▀█▌▐█████▌▐█▀─────────
────────────███████────────────

File: bot.py
Author: Donie Leigh
Email: donie.leigh at gmail.com
Github: https://github.com/xbot
Description: 4WD autobot spirit.
"""

import BaseHTTPServer
import urlparse
import types
import json
from SocketServer import ThreadingMixIn
import RPi.GPIO as GPIO
import os
import threading
import time
import random
import bluetooth as bt

PORT = 8000  # Listening port
PINS = (  # GPIO pin numbers
    22,
    18,
    16,
    12,
    11,
    13,
    5,
    3,
    )
FRONT_SAFETY_DISTANCE = 30  # 30cm

ERR_INVALID_JSON = 1 # Error code, invalid json.

_lock = False  # exclusive lock


def exclusive(fn):
    """ Keep the given function exclusive among threads. """

    def wrapper(*args, **kwds):
        global _lock
        if _lock is True:
            return
        _lock = True
        fn(*args, **kwds)
        _lock = False

    return wrapper


TIMER_STDBY = 'STDBY'
TIMER_MOTOR = 'MOTOR'


class Timer(object):

    _genre = None
    _dataDir = os.path.split(os.path.realpath(__file__))[0] \
        + os.path.sep + 'data'
    _file = None
    _startTime = None
    _data = None

    def __init__(self, genre):
        self._genre = genre
        self._data = {'total': 0, 'thistime': 0}
        self._file = self._dataDir + os.path.sep + genre
        if not os.path.exists(self._dataDir):
            os.mkdir(self._dataDir)
        if os.path.exists(self._file) and os.path.isfile(self._file):
            f = open(self._file, 'r')
            self._data = json.load(f)
            f.close()
        if type(self._data) is not types.DictType \
            or not self._data.has_key('total') \
            or not self._data.has_key('thistime'):
            raise Exception('Invalid timer data.')

    def run(self):
        if self._startTime is None:
            self._startTime = time.time()

    def stop(self, goOn=False):
        if self._startTime is not None:
            delta = time.time() - self._startTime
            self._startTime = goOn and time.time() or None
            self._data['total'] = self._data['total'] + delta
            self._data['thistime'] = self._data['thistime'] + delta
            f = open(self._file, 'w')
            json.dump(self._data, f)
            f.close()

    def stash(self):
        self.stop(True)


class TimerThread(threading.Thread):

    _keepRunning = True
    daemon = True

    def run(self):
        self._timer.run()
        while self._keepRunning is True:
            time.sleep(10)
            self._timer.stash()

    def stop(self):
        self.stopTimer()
        self._keepRunning = False

    def resumeTimer(self):
        self._timer.run()

    def stopTimer(self):
        self._timer.stop()


def singleton(cls):
    """ Set the decorated class singleton. """

    instances = {}

    def _singleton(*args, **kw):
        if cls not in instances:
            instances[cls] = cls(*args, **kw)
        return instances[cls]

    return _singleton


@singleton
class MotorTimerThread(TimerThread):

    _timer = Timer(TIMER_MOTOR)


@singleton
class StdbyTimerThread(TimerThread):

    _timer = Timer(TIMER_STDBY)


def timed(genre, edge=None):
    """ Timing decorator. """

    def _timer(fn):

        def wrapper(*args, **kwds):
            if genre == TIMER_STDBY:
                _stdbyTimerThread = StdbyTimerThread()
                if not _stdbyTimerThread.isAlive():
                    _stdbyTimerThread.start()
            elif genre == TIMER_MOTOR:
                _motorTimerThread = MotorTimerThread()
                if not _motorTimerThread.isAlive():
                    _motorTimerThread.start()
            else:
                raise Exception('Unknown genre: ' + str(genre))

            if genre == TIMER_MOTOR and edge == 0:
                _motorTimerThread.resumeTimer()

            ret = fn(*args, **kwds)

            if genre == TIMER_STDBY:
                _stdbyTimerThread.stop()
            if genre == TIMER_MOTOR and edge == 1:
                _motorTimerThread.stopTimer()

            return ret

        return wrapper

    return _timer


@singleton
class Bot(object):

    """ The Bot Object. """

    BEHAVIOR_NONE = 0  # Completely manual operation.
    BEHAVIOR_ANTICOLLISION = 1  # Manual operation, but stops in a collision threat.
    BEHAVIOR_AUTOMATION = 2  # Completely automation.

    (mtrL1, mtrL2, mtrR1, mtrR2) = (None, None, None, None)  # PWM driven motor inputs.
    (pinTrig, pinEcho, pinInfraredL, pinInfraredR) = (None, None, None,
            None)  # Pins of ultrasonic and infrared sensors.
    _speed = 0  # The current speed.
    _motion = None  # The current motion.
    _keepUltrasonicRunning = False  # Whether to keep ultrasonic thread running.
    _isInfraredEnabled = False  # Whether to enable infrared sensors.
    _ultrasonicThread = None  # The ultrasonic thread.
    _behavior = BEHAVIOR_NONE  # In which behavior the bot works.
    _isFrontBlocked = False  # Whether is there a barrier in the front.
    _direction = None  # <0.5 to turn left, >=0.5 to turn right.
    _sendTime = None # Ultrasonic send time.

    def resume_behavior(fn):
        """ Resume behavior when motion is changed. """

        def wrapper(*args, **kwds):
            this = args[0]
            if (this.getBehavior() == this.BEHAVIOR_ANTICOLLISION
                or this.getBehavior() == this.BEHAVIOR_AUTOMATION) \
                and not this.isUltrasonicEnabled():
                this.startUltrasonic()
                this.startInfrared()
            return fn(*args, **kwds)

        return wrapper

    def __init__(self, pins):
        """ Init Bot instance.

        :pins: tuple, pin numbers being used to control motors
        :returns: void

        """

        if type(pins) != types.TupleType or len(pins) != 8:
            raise TypeError("Parameter 'pins' should be a tuple of eight integers."
                            )

        (
            pinL1,
            pinL2,
            pinR1,
            pinR2,
            self.pinTrig,
            self.pinEcho,
            self.pinInfraredL,
            self.pinInfraredR,
            ) = pins

        GPIO.setmode(GPIO.BOARD)
        GPIO.setup(pinL1, GPIO.OUT)
        GPIO.setup(pinL2, GPIO.OUT)
        GPIO.setup(pinR1, GPIO.OUT)
        GPIO.setup(pinR2, GPIO.OUT)
        self.mtrL1 = GPIO.PWM(pinL1, 50)
        self.mtrL2 = GPIO.PWM(pinL2, 50)
        self.mtrR1 = GPIO.PWM(pinR1, 50)
        self.mtrR2 = GPIO.PWM(pinR2, 50)
        self.mtrL1.start(0)
        self.mtrL2.start(0)
        self.mtrR1.start(0)
        self.mtrR2.start(0)
        self.initUltrasonicSensor()
        self.initInfraredSensors()

    def do(self, command, params=None):
        """ Follow command.

        :command: string, command name.
        :params: array, parameters for the command above.
        :returns: dict, return a dict or None.

        """

        def GetParam(params, key, defaultValue=None):
            """ Parse params and get the value of the key

            :params: dict, parameters dict.
            :key: string, parameter name.
            :defaultValue: mixed, the default value.
            :returns: string

            """

            if type(params) == types.DictType \
                    and params.has_key(key):
                        if type(params[key]) == types.ListType and len(params[key]) > 0:
                            return params[key][0]
                        return params[key]
            return defaultValue

        if type(command) != types.StringType \
                and type(command) != types.UnicodeType:
            raise TypeError('Parameter \'command\' should be a string.')
        if type(params) != types.DictType and params is not None:
            raise TypeError('Parameter \'params\' should be a dict.')

        result = {'command':command, 'speed':None}

        speed = GetParam(params, 'speed', None)
        if command == 'forward':
            self.forward(speed)
        elif command == 'backward':
            self.backward(speed)
        elif command == 'left':
            self.turnLeft(speed)
        elif command == 'right':
            self.turnRight(speed)
        elif command == 'stop':
            holdSpeed = GetParam(params, 'hold', '0')
            self.stop(holdSpeed == '1')
        elif command == 'vary':
            self.setSpeed(speed, True)
        elif command == 'adjustLeft':
            self.adjustLeft()
        elif command == 'adjustRight':
            self.adjustRight()
        elif command == 'resume':
            if ['forward', 'backward'].count(self.getMotion()) > 0:
                self.do(self.getMotion())
        elif command == 'videoOn':
            currentPath = os.path.split(os.path.realpath(__file__))[0]
            resolution = GetParam(params, 'resolution')
            fps = GetParam(params, 'fps')
            options = resolution is not None and ' -r ' + resolution \
                or ''
            options = options + (fps is not None and ' -f ' + fps or '')
            os.system('nohup ' + currentPath + '/video.sh' + options
                      + ' > /dev/null 2>&1 &')
        elif command == 'videoOff':
            os.system('pkill mjpg_streamer')
        elif command == 'behavior':
            behavior = GetParam(params, 'v')
            if behavior is not None and behavior.isdigit():
                self.setBehavior(int(behavior))
            result['behavior'] = self.getBehavior()
        else:
            raise Exception('Unknown command ' + command)

        return result

    @resume_behavior
    @timed(TIMER_MOTOR, 0)
    def forward(self, speed=None, isTmp=False):
        """ Go forward.

        :speed: float, speed percent, 0~100
        :isTmp: bool, True to prevent from changing the global speed.
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if isTmp is not True:
            if speed is not None:
                self.setSpeed(speed)
            speed = self.getSpeed()
        self.setMotion('forward')
        self.mtrL1.ChangeDutyCycle(speed)
        self.mtrL2.ChangeDutyCycle(0)
        self.mtrR1.ChangeDutyCycle(speed)
        self.mtrR2.ChangeDutyCycle(0)

    @timed(TIMER_MOTOR, 0)
    def backward(self, speed=None, isTmp=False):
        """ Go backward.

        :speed: float, speed percent, 0~100
        :isTmp: bool, True to prevent from changing the global speed.
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if isTmp is not True:
            if speed is not None:
                self.setSpeed(speed)
            speed = self.getSpeed()
        self.setMotion('backward')
        self.mtrL1.ChangeDutyCycle(0)
        self.mtrL2.ChangeDutyCycle(speed)
        self.mtrR1.ChangeDutyCycle(0)
        self.mtrR2.ChangeDutyCycle(speed)

    @timed(TIMER_MOTOR, 0)
    def turnLeft(self, speed=None, isTmp=False):
        """ Turn left.

        :speed: float, speed percent, 0~100
        :isTmp: bool, True to prevent from changing the global speed.
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if isTmp is not True:
            if speed is not None:
                self.setSpeed(speed)
            speed = self.getSpeed()
        self.setMotion('left')
        self.mtrL1.ChangeDutyCycle(0)
        self.mtrL2.ChangeDutyCycle(speed)
        self.mtrR1.ChangeDutyCycle(speed)
        self.mtrR2.ChangeDutyCycle(0)

    @timed(TIMER_MOTOR, 0)
    def turnRight(self, speed=None, isTmp=False):
        """ Turn right.

        :speed: float, speed percent, 0~100
        :isTmp: float, True to prevent from changing the global speed.
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if isTmp is not True:
            if speed is not None:
                self.setSpeed(speed)
            speed = self.getSpeed()
        self.setMotion('right')
        self.mtrL1.ChangeDutyCycle(speed)
        self.mtrL2.ChangeDutyCycle(0)
        self.mtrR1.ChangeDutyCycle(0)
        self.mtrR2.ChangeDutyCycle(speed)

    @timed(TIMER_MOTOR, 0)
    def adjustLeft(self):
        """ Turn left a little bit.

        :returns: void

        """

        speed = self.getSpeed()
        tmpSpeed = speed >= 40 and speed - 40 or 0
        if self.getMotion() == 'forward':
            self.mtrL1.ChangeDutyCycle(tmpSpeed)
            self.mtrL2.ChangeDutyCycle(0)
            self.mtrR1.ChangeDutyCycle(speed)
            self.mtrR2.ChangeDutyCycle(0)
        elif self.getMotion() == 'backward':
            self.mtrL1.ChangeDutyCycle(0)
            self.mtrL2.ChangeDutyCycle(tmpSpeed)
            self.mtrR1.ChangeDutyCycle(0)
            self.mtrR2.ChangeDutyCycle(speed)

    @timed(TIMER_MOTOR, 0)
    def adjustRight(self):
        """ Turn right a little bit.

        :returns: void

        """

        speed = self.getSpeed()
        tmpSpeed = speed >= 40 and speed - 40 or 0
        if self.getMotion() == 'forward':
            self.mtrL1.ChangeDutyCycle(speed)
            self.mtrL2.ChangeDutyCycle(0)
            self.mtrR1.ChangeDutyCycle(tmpSpeed)
            self.mtrR2.ChangeDutyCycle(0)
        elif self.getMotion() == 'backward':
            self.mtrL1.ChangeDutyCycle(0)
            self.mtrL2.ChangeDutyCycle(speed)
            self.mtrR1.ChangeDutyCycle(0)
            self.mtrR2.ChangeDutyCycle(tmpSpeed)

    @timed(TIMER_MOTOR, 1)
    def stop(self, holdSpeed=False):
        """ Stop moving.

        :holdSpeed: boolean, True to stop when holding the speed.
        :returns: void

        """

        if ['forward', 'backward'].count(self.getMotion()) > 0 \
            and self.getSpeed() > 20:
            tmp = self.getSpeed()
            while tmp > 0:
                tmp = tmp - 20
                if self.getMotion() == 'forward':
                    self.forward(tmp, True)
                elif self.getMotion() == 'backward':
                    self.backward(tmp, True)
                time.sleep(0.1)

        if self.isUltrasonicEnabled():
            self.stopUltrasonic()
            self.stopInfrared()

        if holdSpeed is False:
            self.setSpeed(0)

        self.setMotion(None)
        self.mtrL1.ChangeDutyCycle(0)
        self.mtrL2.ChangeDutyCycle(0)
        self.mtrR1.ChangeDutyCycle(0)
        self.mtrR2.ChangeDutyCycle(0)

    def setSpeed(self, speed, apply=False):
        """ Change the current speed.

        :speed: string, speed percent or increment and decrement.
        :apply: bool, whether to apply the speed immediately
        :returns: void

        """

        if (type(speed) == types.StringType or type(speed) == types.UnicodeType) \
                and speed.startswith(('-', '+')):
            tmp = speed[0] == '+' and self.getSpeed() + int(speed[1:]) \
                or self.getSpeed() - int(speed[1:])
            if tmp >= 0 and tmp <= 100:
                self._speed = tmp
        else:
            speed = float(speed)
            if speed < 0 or speed > 100:
                raise ValueError('Parameter \'speed\' should be an float number between 0 and 100.'
                                 )
            self._speed = speed

        if apply is True and self.getMotion() is not None:
            self.do(self.getMotion())

    def getSpeed(self):
        """ Return the current speed.

        :returns: float

        """

        return self._speed

    def setMotion(self, motion):
        """ Set the current motion

        :motion: string, the new motion
        :returns: void

        """

        self._motion = motion

    def getMotion(self):
        """ Return the current motion

        :returns: string

        """

        return self._motion

    def getBehavior(self):
        """ In which behavior the bot works.

        :returns: int

        """

        return self._behavior

    def setBehavior(self, behavior):
        """ Set in which behavior the bot works.

        :behavior: int
        :returns: void

        """

        self._behavior = behavior

        if behavior == self.BEHAVIOR_AUTOMATION or behavior \
            == self.BEHAVIOR_ANTICOLLISION:
            if not self.isUltrasonicEnabled():
                self.startUltrasonic()
                self.startInfrared()

        if behavior == self.BEHAVIOR_NONE:
            self.stopUltrasonic()
            self.stopInfrared()

    def initUltrasonicSensor(self):
        """ Initialize ultrasonic function.

        :returns: void

        """

        GPIO.setup(self.pinTrig, GPIO.OUT)
        GPIO.setup(self.pinEcho, GPIO.IN)

        @exclusive
        def on_echo(channel):
            """ Record the time when a wave is sent out or calculate 
            the distance when a wave is received, then react on the distance.
            """

            def act_on_my_own(distance):
                """ Act on the bot's own. """

                if distance < FRONT_SAFETY_DISTANCE:
                    self._isFrontBlocked = True
                else:
                    self._isFrontBlocked = False

                states = self.getSensorStates()
                self.actOnMyOwn(states)

            def stop_on_collision_threat(distance):
                """ Stop if there is a collision threat. """

                if distance < FRONT_SAFETY_DISTANCE:
                    self.stop(True)

            # Read PWL, high for rising edge and low for falling edge.

            try:
                pwl = GPIO.input(self.pinEcho)
            except RuntimeError:
                return

            if pwl == GPIO.HIGH:
                self._sendTime = time.time()
            else:
                delta = time.time() - self._sendTime
                if 0.0235 > delta > 0.00015:  # Consider a distance between 2 and 400 cm as a reasonable value
                    distance = round(delta * 34000 / 2, 2)
                    if self.getBehavior() \
                        == self.BEHAVIOR_ANTICOLLISION:
                        stop_on_collision_threat(distance)
                    elif self.getBehavior() == self.BEHAVIOR_AUTOMATION:
                        act_on_my_own(distance)

        GPIO.add_event_detect(self.pinEcho, GPIO.BOTH, callback=on_echo)

    def startUltrasonic(self):
        """ Start a new ultrasonic thread.

        :returns: void

        """

        if self.isUltrasonicEnabled():
            return

        def keep_checking_front():
            """ Keep sending waves. """

            try:
                self._keepUltrasonicRunning = True
                while self._keepUltrasonicRunning:
                    GPIO.output(self.pinTrig, GPIO.HIGH)
                    time.sleep(0.00001)
                    GPIO.output(self.pinTrig, GPIO.LOW)
                    time.sleep(0.1)
            except RuntimeError:
                self._keepUltrasonicRunning = False
                return

        self._ultrasonicThread = \
            threading.Thread(target=keep_checking_front)
        self._ultrasonicThread.start()

    def stopUltrasonic(self):
        """ Stop the ultrasonic thread.

        :returns: void

        """

        self._keepUltrasonicRunning = False

    def isUltrasonicEnabled(self):
        """ Check whether ultrasonic is running.

        :returns: bool

        """

        return isinstance(self._ultrasonicThread, threading.Thread) \
            and self._ultrasonicThread.isAlive()

    def initInfraredSensors(self):
        """ Initialize infrared sensors.

        :returns: void

        """

        def on_infrared(channel):
            if self.getBehavior() == self.BEHAVIOR_AUTOMATION \
                and self.isUltrasonicEnabled() \
                and self.isInfraredEnabled():
                states = self.getSensorStates()
                self.actOnMyOwn(states)

        GPIO.setup(self.pinInfraredL, GPIO.IN)
        GPIO.setup(self.pinInfraredR, GPIO.IN)
        GPIO.add_event_detect(self.pinInfraredL, GPIO.BOTH,
                              callback=on_infrared)
        GPIO.add_event_detect(self.pinInfraredR, GPIO.BOTH,
                              callback=on_infrared)

    def startInfrared(self):
        """ Enable infrared sensors.

        :returns: bool

        """

        self._isInfraredEnabled = True

    def stopInfrared(self):
        """ Disable infrared sensors.

        :returns: bool

        """

        self._isInfraredEnabled = False

    def isInfraredEnabled(self):
        """ Whether infrared sensors are enabled.

        :returns: bool

        """

        return self._isInfraredEnabled

    def getSensorStates(self):
        """ Return the states of those sensors.

        :returns: list, [front, left, right]

        """

        return [self.isFrontBlocked(), self.isLeftBlocked(),
                self.isRightBlocked()]

    def isFrontBlocked(self):
        """ Check if there is a barrier in the front.

        :returns: bool

        """

        return self._isFrontBlocked

    def isLeftBlocked(self):
        """ Check if there is a barrier on the left side. 
        
        :returns: bool

        """

        return GPIO.input(self.pinInfraredL) == GPIO.LOW

    def isRightBlocked(self):
        """ Check if there is a barrier on the right side. 
        
        :returns: bool

        """

        return GPIO.input(self.pinInfraredR) == GPIO.LOW

    def actOnMyOwn(self, states):
        """ Let the bot descide how to act on the given sensor states. 
        
        :states: list, [front, left, right]
        :returns: void

        """

        forwardStates = [[False, False, False], [False, True, True]]
        leftStates = [[False, False, True], [True, False, True]]
        rightStates = [[False, True, False], [True, True, False]]
        randomStates = [[True, False, False], [True, True, True]]
        if forwardStates.count(states) > 0:
            if self.getMotion() != 'forward':
                self.forward()
            self._direction = None
        elif leftStates.count(states) > 0:
            if self.getMotion() != 'left':
                self.turnLeft(80, True)
        elif rightStates.count(states) > 0:
            if self.getMotion() != 'right':
                self.turnRight(80, True)
        elif randomStates.count(states) > 0:
            if ['left', 'right'].count(self.getMotion()) > 0:
                return
            if self._direction is None:
                self._direction = random.random()
            if self._direction < 0.5:
                self.turnLeft(80, True)
            else:
                self.turnRight(80, True)
        else:
            raise Exception('Unknown sensor states: ' + str(states))


class RequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):

    """ My HTTP Handler. """

    bot = Bot(PINS)

    def json_response(fn):

        def wrapper(*args, **kwds):
            this = args[0]
            response = fn(*args, **kwds)
            this.send_response(200)
            this.end_headers()
            this.wfile.write(json.dumps(response))
            this.wfile.write('\n')

        return wrapper

    @json_response
    def do_GET(self):
        """ Handle GET requests

        :returns: void

        """

        response = {'code': 0, 'msg': '', 'data': None}
        pathInfo = urlparse.urlparse(self.path)
        command = pathInfo.path.strip(' /')
        params = urlparse.parse_qs(pathInfo.query, True)

        if command == 'connect':
            response['data'] = {'command':'connect', 'behavior':self.bot.getBehavior()}
            return response

        try:
            response['data'] = self.bot.do(command, params)
        except Exception, e:
            response['code'] = 1
            response['msg'] = str(e)
            return response

        return response


class ThreadedServer(ThreadingMixIn, BaseHTTPServer.HTTPServer):

    """ HTTP Server supporting threading. """


@timed(TIMER_STDBY)
def httpd():
    threadName = '@' + threading.currentThread().getName() + ':'
    print threadName, 'Listening on port', PORT, ', press <Ctrl-C> to stop.'
    srv = ThreadedServer(('', PORT), RequestHandler)
    srv.serve_forever()


@timed(TIMER_STDBY)
def bluetoothd():
    srvSock = bt.BluetoothSocket(bt.RFCOMM)
    srvSock.bind(("", bt.PORT_ANY))
    srvSock.listen(1)

    threadName = '@' + threading.currentThread().getName() + ':'
    port = srvSock.getsockname()[1]
    uuid = "00001101-0000-1000-8000-00805F9B34FB"

    bt.advertise_service(srvSock, "PiBTSrv",
        service_id = uuid,
        service_classes = [uuid, bt.SERIAL_PORT_CLASS],
        profiles = [bt.SERIAL_PORT_PROFILE],
    )

    bot = Bot(PINS)
                       
    try:
        while True:
            print threadName, "Waiting for connection on RFCOMM channel %d" % port

            cliSock, cliInfo = srvSock.accept()
            print threadName, "Accepted connection from ", cliInfo

            try:
                while True:
                    resp = {'code':0, 'msg':'', 'data':None}

                    data = cliSock.recv(1024)
                    if len(data) == 0: break
                    print threadName, "Received [%s]" % data

                    try:
                        cmd = json.loads(data)

                        if cmd['command'] == 'connect':
                            resp['data'] = {
                                'command':'connect',
                                'behavior':bot.getBehavior()
                            }
                        else:
                            try:
                                resp['data'] = bot.do(cmd['command'], cmd['params'])
                            except Exception, e:
                                resp['code'] = 1
                                resp['msg'] = str(e)
                    except ValueError:
                        resp['code'] = ERR_INVALID_JSON
                        resp['msg'] = 'Invalid JSON.'

                    cliSock.send(json.dumps(resp))
            except IOError:
                pass

            print threadName, "Disconnected from ", cliInfo

            cliSock.close()
    finally:
        srvSock.close()
    

@timed(TIMER_STDBY)
def both():
    t1 = threading.Thread(target=bluetoothd, name='Bluetoothd')
    t1.daemon = True
    t1.start()
    t2 = threading.Thread(target=httpd, name='Httpd')
    t2.daemon = True
    t2.start()
    while True:
        time.sleep(60)


if __name__ == '__main__':
    try:
        # both()
        # httpd()
        bluetoothd()
    except KeyboardInterrupt:
        print 'Game over.'
    finally:
        GPIO.cleanup()
