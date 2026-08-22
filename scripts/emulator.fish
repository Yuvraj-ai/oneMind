#!/usr/bin/env fish
#
# Boot / stop the headless test emulator used for instrumented tests.
#
#   scripts/emulator.fish start   # boot and block until fully booted
#   scripts/emulator.fish stop
#   scripts/emulator.fish status
#
# Requires the AVD created per BUILDING.md:
#   avdmanager create avd -n onemind_test \
#     -k "system-images;android-36;google_apis;x86_64" -d pixel_6

set -l AVD_NAME onemind_test
set -l SDK $ANDROID_HOME
test -z "$SDK"; and set SDK $HOME/Android/Sdk
set -l ADB $SDK/platform-tools/adb
set -l EMU $SDK/emulator/emulator

switch "$argv[1]"
    case start
        if $ADB devices | grep -q emulator-
            echo "Emulator already running."
            exit 0
        end

        echo "Booting $AVD_NAME (headless)..."
        # swiftshader_indirect keeps this working on machines with no usable GPU.
        $EMU -avd $AVD_NAME -no-window -no-audio -no-boot-anim \
            -gpu swiftshader_indirect -no-snapshot-save \
            > /tmp/onemind-emulator.log 2>&1 &

        $ADB wait-for-device

        echo -n "Waiting for boot"
        for i in (seq 1 60)
            set -l booted ($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
            if test "$booted" = "1"
                echo " done ("(math $i \* 5)"s)"
                $ADB devices
                exit 0
            end
            echo -n "."
            sleep 5
        end

        echo " TIMED OUT after 300s. See /tmp/onemind-emulator.log"
        exit 1

    case stop
        $ADB emu kill 2>/dev/null; or pkill -f "qemu-system.*$AVD_NAME"
        echo "Emulator stopped."

    case status
        $ADB devices
        set -l api ($ADB shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')
        test -n "$api"; and echo "API level: $api"

    case '*'
        echo "usage: scripts/emulator.fish {start|stop|status}"
        exit 1
end
