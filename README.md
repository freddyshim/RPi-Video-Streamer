Project-Pigeon
==========
Connect a Raspberry Pi w/ a camera module to an Android device and stream the ivideo output to your favorite streaming site.

Raspberry Pi Setup
-------
### 1. Enabling OTG
We need to edit some files on Raspberry Pi's boot disk. Add the following line at the bottom of /boot/config.txt:
```
dtoverlay=dwc2
```
Add the following at the end of the line of /boot/cmdline.txt:
```
modules-load=dwc2,libcomposite
```
If you own a Raspberry Pi Zero W and would like to debug over a WiFi connection, run the following command in the /boot directory:
```
touch ssh
```
### 2. Enable Camera
Connect your camera module to the Raspberry Pi if you already haven't done so. Then, you can find the menu to enable the camera by running the command:
```
sudo raspi-config
```

### 3. Configure Pi as a UVC Gadget
Check out a forked version of uvc-gadget:
```
cd /home/pi
git clone https://github.com/climberhunt/uvc-gadget.git
```
This repository provides a script that creates a systemd service. To install this, copy the file to the specified location and enable it.
```
cd uvc-gadget
sudo cp piwebcam.service /etc/systemd/system/
sudo systemctl enable piwebcam
```
Now, we build the uvc-gadget app. While you are in the /uvc-gadget directory, compile the repository code:
```
make
```
Restart your Raspberry Pi. Upon next boot, your Raspberry Pi should be set up as a USB camera gadget.

More information can be found at the following links:
https://www.raspberrypi.org/forums/viewtopic.php?t=148361
http://www.davidhunt.ie/raspberry-pi-zero-with-pi-camera-as-usb-webcam/

Android Streaming App
-------
Our Android app needs to find the connected UVC gadget at the USB port and retrieve the video feed from the camera module. In order to achieve this, we will use the following library:
https://github.com/saki4510t/UVCCamera

