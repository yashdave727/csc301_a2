# Makefile for stress_tests.c
CC = gcc
CFLAGS = -Wall -Wextra -std=c99
LIBS = -lcurl -lpthread

SRCS = stress_test.c
OBJS = $(SRCS:.c=.o)
TARGET = stress_test

.PHONY: all clean

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) $(OBJS) -o $(TARGET) $(LIBS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)
