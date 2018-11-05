#!/bin/sh
CONF="/etc/config/qpkg.conf"
QPKG_NAME="filebot"
QPKG_ROOT=$(/sbin/getcfg $QPKG_NAME Install_Path -f $CONF)


case "$1" in
	start)
		ENABLED=$(/sbin/getcfg $QPKG_NAME Enable -u -d FALSE -f $CONF)
		if [ "$ENABLED" != "TRUE" ]; then
			echo "$QPKG_NAME is disabled."
			exit 1
		fi

		/bin/ln -sf "$QPKG_ROOT/bin/filebot.sh" "/usr/bin/filebot"
		/bin/ln -sf "$QPKG_ROOT" "/opt/filebot"
		;;

	stop)
		rm -rf "/usr/bin/filebot"
		rm -rf "/opt/filebot"
		;;

	restart)
		$0 stop
		$0 start
		;;

	*)
		echo "Usage: $0 {start|stop|restart}"
		exit 1
esac


exit 0
