"""Inject spec['missile'] (missilekit) into the build output of a vehicle: inj.py NAME [NAME...]"""
import json, sys
from pathlib import Path
import missilekit as mk
for n in sys.argv[1:]:
    sp = json.loads((Path(__file__).resolve().parent / f'{n}.json').read_text())
    if 'missile' in sp:
        mk.inject(n, sp['missile'])
        print(n, 'missile injected')
