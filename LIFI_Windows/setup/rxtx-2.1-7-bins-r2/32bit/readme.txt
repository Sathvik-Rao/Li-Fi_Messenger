Install RXTX for all platforms ->  http://rxtx.qbang.org/pub/rxtx/rxtx-2.1-7-bins-r2.zip

Official Website -> http://rxtx.qbang.org/wiki/index.php/Main_Page

Extract installed file
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

Installation for Windows
------------------------------
RXTX installation procedure if you want to run RXTX enabled programs ==If you just want to run RXTX enabled programs, follow this procedure.

Identify your Java Runtime Environment's folder. For version 1.6.0, this usually is c:\Program Files\Java\jre1.6.0_01\ 

    from installed folder open rxtx-2.1-7-bins-r2\Windows\i368-mingw32 and
    Copy rxtxParallel.dll to c:\Program Files\Java\jdk1.8.0_161\jre\bin\        (or) C:\Program Files\Java\jre1.8.0_241\bin
    Copy rxtxSerial.dll to c:\Program Files\Java\jdk1.8.0_161\jre\bin\          (or) C:\Program Files\Java\jre1.8.0_241\bin
    Copy RXTXcomm.jar to c:\Program Files\Java\jdk1.8.0_161\jre\lib\ext\  (or)  C:\Program Files\Java\jre1.8.0_241\lib\ext

NOTE: download 64bit version of rxtx if JVM is 64bit (website -> http://fizzed.com/oss/rxtx-for-java)

NOTE: When installing on Windows XP Embedded, make sure you include crtdll.dll (in the C Runtime Component) as it is required by rxtxSerial.dll 

---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

Similarly other platforms see official website for help.
