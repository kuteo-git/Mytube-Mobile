# Every element on the simulator, from idb, as "x,y type label" in points.
# usage: sim-ui.py list | sim-ui.py find REGEX   (prints the first match's centre)
import json,subprocess,sys,re
U="C6825263-6297-40D0-A994-72E4AFC851E7"
els=json.loads(subprocess.run(["idb","ui","describe-all","--udid",U],capture_output=True,text=True).stdout)
if sys.argv[1]=="list":
    for e in els:
        f=e["frame"]; print(f'{int(f["x"]+f["width"]/2):4d},{int(f["y"]+f["height"]/2):4d} {e["type"][:6]:6s} {(e["AXLabel"] or "")[:90]}')
else:  # find REGEX -> center of first match
    for e in els:
        if re.search(sys.argv[2], e["AXLabel"] or ""):
            f=e["frame"]; print(int(f["x"]+f["width"]/2), int(f["y"]+f["height"]/2)); break
