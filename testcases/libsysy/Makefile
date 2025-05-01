CC=gcc
CXX=g++
AR=ar

clean:
	$(RM) $(wildcard *.o) $(wildcard *.a)

%.o: %.c
	$(CC) -c $< -o $@

%.o: %.cc
	$(CXX) -c $< -o $@

libsysy.a: sylib.o sytimer.o
	$(AR) rcs $@ $^

build: libsysy.a

.PHONY: build

.DEFAULT_GOAL := build
