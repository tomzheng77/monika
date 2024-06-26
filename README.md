MOD 1-1 Monika
see also:
- MOD 1-2: OrBit
- MOD 1-3: Wallhack

# Summary

force yourself to the Gym every day!
focus on work with restricted websites, programs and files!
make yourself be at a place at a time!
allow yourself to enjoy for a while, then come back to work with full dedication!

# Potential

- exercise every day at the Gym
- wake up at 7:00
- work on anything on the computer
    - game development
    - study
- indulge but not binge in games
    - Team Fortress
    - YouTube

# Components

- systemctl service: starts monika automatically with root
- mon: communicates with monika to request commands
- monika.jar: contains the monika program

these are all the components required for monika, it does not require editing the PATH
monika can reload configuration without restarting, restart is only necessary when the jarfile
has been updated

it does not depend on the following (compared to lockon):
- /etc/profile
- crontab
- at and atq
- individual shell scripts

# Environment

monika assumes these external components have been set up correctly

- 'profile' user
- 'unlocker' user
- /etc/sudoers granting su without password for 'unlocker'
- /etc/environment with http_proxy and https_proxy
- /home/shared/
    - projects/: an empty folder with read/write permission
    - proxy/: containing proxy certificates

WARNING: while developing monika, do not remove the original lockon
otherwise you may accidentally lock yourself out
