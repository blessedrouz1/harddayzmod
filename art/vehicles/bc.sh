#!/bin/zsh
# build + shade + check one vehicle spec: bc.sh name
SK=/Users/danylokulibaba/Desktop/minecraft-mods-builder/blockbench-military-pixel
D=/Users/danylokulibaba/Desktop/dayz/harddayzmod-main/art/vehicles
OUT=$(python3 $SK/scripts/build_vehicle.py $D/$1.json -o $D/out 2>&1)
if echo "$OUT" | grep -q "spec error\|Error\|Traceback"; then echo "$OUT" | head -8; exit 1; fi
python3 $D/shade.py $1 && python3 $D/wheels.py $1 && ANIM=$D/out/$1.animation.json; [ -f $ANIM ] || ANIM=""
python3 $SK/scripts/check_model.py --max ${2:-60} $D/out/$1.geo.json $D/out/$1.png $ANIM | grep -v "^NOTE"
python3 $D/inj.py $1
python3 $D/attach.py $1 | grep -v "^   FLOAT .grab" 
