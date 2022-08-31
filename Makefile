all:
	javac -d out Main.java
run:
	cd out && java Main 228.5.6.7 "Ola"