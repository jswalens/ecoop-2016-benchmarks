import sys

t = int(sys.argv[1])
max = int(sys.argv[2])

i = 1
while i*t <= max:
	print i
	i *= 2
