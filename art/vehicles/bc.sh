#!/bin/zsh
# build + shade + check one vehicle spec: bc.sh name
SK=/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel
D=/Users/danylokulibaba/Desktop/dayz/harddayzmod-main/art/vehicles
python3 $SK/scripts/build_vehicle.py $D/$1.json -o $D/out | tail -3 && python3 $D/shade.py $1 && python3 $SK/scripts/check_model.py --max 60 $D/out/$1.geo.json $D/out/$1.png $D/out/$1.animation.json
