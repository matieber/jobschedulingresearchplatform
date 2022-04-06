'''
given a float value which represents time_in_seconds with fractional part, the method returns an integer (using
round function) of its conversion to milliseconds
'''
import math


def to_milliseconds(time_in_seconds):
    return math.ceil(time_in_seconds * 1000)

def from_nano_to_milliseconds(time_in_nanoseconds):
    return math.ceil(time_in_nanoseconds / 1000000)
