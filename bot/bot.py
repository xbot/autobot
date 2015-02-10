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

PORT = 8000  # Listening port
PINS = (12, 16, 18, 22)  # GPIO pin numbers


class Bot(object):

    """ The Bot Object. """

    (mtrL1, mtrL2, mtrR1, mtrR2) = (None, None, None, None)  # PWM driven motor inputs
    _speed = 0  # the current speed
    _motion = None  # the current motion
    _keepUltrasonicRunning = False  # whether to keep ultrasonic thread running
    _ultrasonicThread = None  # The ultrasonic thread

    def __init__(self, pins):
        """ Init Bot instance.

        :pins: tuple, four pin numbers being used to control motors
        :returns: void

        """

        if type(pins) != types.TupleType or len(pins) != 4:
            raise TypeError('Parameter \'pins\' should be a tuple of four integers.'
                            )
        (pinL1, pinL2, pinR1, pinR2) = pins

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

    def do(self, command, params=None):
        """ Follow command.

        :command: string, command name.
        :params: array, parameters for the command above.
        :returns: void

        """

        def GetParam(params, key, defaultValue=None):
            """ Parse params and get the value of the key

            :params: dict, parameters dict.
            :key: string, parameter name.
            :defaultValue: mixed, the default value.
            :returns: string

            """

            return type(params) == types.DictType \
                and params.has_key(key) and type(params[key]) \
                == types.ListType and len(params[key]) > 0 \
                and params[key][0] or defaultValue

        if type(command) != types.StringType:
            raise TypeError('Parameter \'command\' should be a string.')
        if type(params) != types.DictType and params is not None:
            raise TypeError('Parameter \'params\' should be a dict.')

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
        else:
            raise Exception('Unknown command ' + command)

    def forward(self, speed=None):
        """ Go forward.

        :speed: float, speed percent, 0~100
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if speed is not None:
            self.setSpeed(speed)
        speed = self.getSpeed()
        self.setMotion('forward')
        self.mtrL1.ChangeDutyCycle(speed)
        self.mtrL2.ChangeDutyCycle(0)
        self.mtrR1.ChangeDutyCycle(speed)
        self.mtrR2.ChangeDutyCycle(0)

    def backward(self, speed=None):
        """ Go backward.

        :speed: float, speed percent, 0~100
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if speed is not None:
            self.setSpeed(speed)
        speed = self.getSpeed()
        self.setMotion('backward')
        self.mtrL1.ChangeDutyCycle(0)
        self.mtrL2.ChangeDutyCycle(speed)
        self.mtrR1.ChangeDutyCycle(0)
        self.mtrR2.ChangeDutyCycle(speed)

    def turnLeft(self, speed=None):
        """ Turn left.

        :speed: float, speed percent, 0~100
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if speed is not None:
            self.setSpeed(speed)
        speed = self.getSpeed()
        self.setMotion('left')
        self.mtrL1.ChangeDutyCycle(0)
        self.mtrL2.ChangeDutyCycle(speed)
        self.mtrR1.ChangeDutyCycle(speed)
        self.mtrR2.ChangeDutyCycle(0)

    def turnRight(self, speed=None):
        """ Turn right.

        :speed: float, speed percent, 0~100
        :returns: void

        """

        if (speed is None or float(speed) <= 0) and (self.getSpeed()
                is None or self.getSpeed() <= 0):
            speed = 20
        if speed is not None:
            self.setSpeed(speed)
        speed = self.getSpeed()
        self.setMotion('right')
        self.mtrL1.ChangeDutyCycle(speed)
        self.mtrL2.ChangeDutyCycle(0)
        self.mtrR1.ChangeDutyCycle(0)
        self.mtrR2.ChangeDutyCycle(speed)

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

    def stop(self, holdSpeed=False):
        """ Stop moving.

        :holdSpeed: boolean, True to stop when holding the speed.
        :speed: float, speed percent, 0~100
        :returns: void

        """

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

        if type(speed) == types.StringType and speed.startswith(('-',
                '+')):
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

    def performUltrasonic(self, pins):
        """ Do distance detection leveraging ultrasonic.

        :returns: void

        """

        # pinTrig = 21
        # pinEcho = 23

        (pinTrig, pinEcho) = pins
        GPIO.setup(pinTrig, GPIO.OUT)
        GPIO.setup(pinEcho, GPIO.IN)

        def calc_distance(channel):
            if GPIO.input(pinEcho) == GPIO.HIGH:
                self._startTime = time.time()
            else:
                self._endTime = time.time()
                delta = self._endTime - self._startTime
                if 0.0235 > delta > 0.00015:
                    print round(delta * 340 / 2, 2), delta

        GPIO.add_event_detect(pinEcho, GPIO.BOTH,
                              callback=calc_distance)

        def keep_checking_front():
            self._keepUltrasonicRunning = True
            while self._keepUltrasonicRunning:
                GPIO.output(pinTrig, GPIO.HIGH)
                time.sleep(0.00001)
                GPIO.output(pinTrig, GPIO.LOW)
                time.sleep(0.1)

        self._ultrasonicThread = \
            threading.Thread(target=keep_checking_front)
        self._ultrasonicThread.start()

    def stopUltrasonic(self):
        """ Stop the ultrasonic thread.

        :returns: void

        """

        self._keepUltrasonicRunning = False

    def isUltrasonicRunning(self):
        """ Check whether ultrasonic is running.

        :returns: bool

        """

        return isinstance(self._ultrasonicThread, threading.Thread) \
            and self._ultrasonicThread.isAlive()


class RequestHandler(BaseHTTPServer.BaseHTTPRequestHandler):

    """ My HTTP Handler. """

    bot = Bot(PINS)

    def do_GET(self):
        """ Handle GET requests

        :returns: void

        """

        pathInfo = urlparse.urlparse(self.path)
        command = pathInfo.path.strip(' /')
        params = urlparse.parse_qs(pathInfo.query, True)

        # make connection

        if command == 'connect':
            return self._respond('ok')

        try:
            self.bot.do(command, params)
        except Exception, e:
            return self._respond(str(e), 1)

        return self._respond(self.bot.getSpeed())

    def _respond(self, msg='', code=0):
        """ Send response to the userside.

        :msg: string, response data.
        :code: int, status code, 0 for success, 1 for normal failure.
        :returns: void

        """

        response = {'code': code, 'msg': msg}
        self.send_response(200)
        self.end_headers()
        self.wfile.write(json.dumps(response))
        self.wfile.write('\n')


class ThreadedServer(ThreadingMixIn, BaseHTTPServer.HTTPServer):

    """ HTTP Server supporting threading. """


if __name__ == '__main__':
    httpd = ThreadedServer(('', PORT), RequestHandler)
    print 'Listening on port', PORT, ', press <Ctrl-C> to stop.'
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print 'Game over.'
    GPIO.cleanup()
