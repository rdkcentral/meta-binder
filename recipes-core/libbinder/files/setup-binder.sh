#!/bin/bash

readonly BINDERFS_PATH="/dev/binderfs"

echo "Creating binderfs mount directory..."
mkdir -p ${BINDERFS_PATH}

if ! mountpoint -q ${BINDERFS_PATH}; then
    echo "Mounting binder filesystem..."
    if ! mount -t binder binder ${BINDERFS_PATH}; then
        echo "Error: Failed to mount binder filesystem."
        exit 1
    fi
else
    echo "Binder filesystem already mounted."
fi

chgrp vpu ${BINDERFS_PATH}/binder
chmod g+rw ${BINDERFS_PATH}/binder

# Use Bind mount instead of symlink because Dobby container handles "devNodes"
# and "extraMounts" differently. Device node should be mounted using "devNodes"
# instead of creating symlink via "extraMounts", by setting "source" and
# "destination" differently
echo "Creating Bind mount for the binder device..."
touch /dev/binder
umount -q /dev/binder
mount -B ${BINDERFS_PATH}/binder /dev/binder

echo "Binder setup completed."
