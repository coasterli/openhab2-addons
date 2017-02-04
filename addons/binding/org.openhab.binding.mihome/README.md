# Xiaomi Mi Smart Home Binding for openHAB 2

## How to set up
1. Install the binding
2. Setup gateway to be discoverable
   1. Add Gateway 2 to your WiFi Network
   2. Install [Mi Home app](https://play.google.com/store/apps/details?id=com.xiaomi.smarthome)
   3. Set your region to Mainland China under Settings -> Locale (seems to be required)
   4. Update gateway (maybe multiple times)
   5. Enable developer mode:<br />
      Select your Gateway in Mi Home, then the 3 dots at the top right of the screen, then click on about.
      Tap the version number (or empty part) at the bottom of the screen repeatedly until you enable 
      developer mode - you should now have 2 extra options listed in Chinese. Choose the first new option, and then tap the 
      toggle switch to enable LAN functions. Note down the developer key (something like: 91bg8zfkf9vd6uw7).
      Make sure you hit the OK button (to the right of the cancel button) to save your changes.
4. In openHAB you should now be able to discover the Xiaomi Gateway
5. Enter the previously noted developer key in openHAB Paper UI -> Configuration -> Things -> Xiaomi Gateway -> Edit -> Developer Key. Save.
   (This is required if you want to be able to send controls to the devices like the light of the gateway.)
6. Add your sensor to the gateway by following the instructions in the Mi Home app.
7. Your sensors should be getting discovered by openHAB as you use them.

## Important information
This requires port 9898 to not be used by any other service on the system.

