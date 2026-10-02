// Synthesised touches on the iOS Simulator window, in device points.
// usage: touch tap X Y | touch drag X1 Y1 X2 Y2 [ms] [holdMs] | touch longpress X Y [ms]
import Foundation
import CoreGraphics
import AppKit

func run(_ s: String) -> String {
  let p = Process(); p.launchPath = "/usr/bin/osascript"; p.arguments = ["-e", s]
  let o = Pipe(); p.standardOutput = o; try! p.run(); p.waitUntilExit()
  return String(data: o.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8)!.trimmingCharacters(in: .whitespacesAndNewlines)
}
_ = run("tell application \"Simulator\" to activate")
usleep(250_000)
let g = run("tell application \"System Events\" to tell process \"Simulator\" to get {position, size} of window 1")
  .split(separator: ",").map { Double($0.trimmingCharacters(in: .whitespaces))! }
let ox = g[0], oy = g[1] + (g[3] - 844.0)   // iPhone 16e screen is 390x844 pt; the rest is the title bar
func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: ox + x, y: oy + y) }
func post(_ t: CGEventType, _ p: CGPoint) {
  CGEvent(mouseEventSource: nil, mouseType: t, mouseCursorPosition: p, mouseButton: .left)!.post(tap: .cghidEventTap)
}
let a = CommandLine.arguments.dropFirst().map { $0 }
let n = a.dropFirst().compactMap { Double($0) }
switch a.first {
case "tap":
  post(.mouseMoved, pt(n[0], n[1])); usleep(60_000)
  post(.leftMouseDown, pt(n[0], n[1])); usleep(70_000); post(.leftMouseUp, pt(n[0], n[1]))
case "longpress":
  post(.leftMouseDown, pt(n[0], n[1])); usleep(useconds_t((n.count > 2 ? n[2] : 800) * 1000)); post(.leftMouseUp, pt(n[0], n[1]))
case "drag":
  let ms = n.count > 4 ? n[4] : 400, hold = n.count > 5 ? n[5] : 0
  let steps = max(10, Int(ms / 16))
  post(.leftMouseDown, pt(n[0], n[1])); usleep(50_000)
  for i in 1...steps {
    let f = Double(i) / Double(steps)
    post(.leftMouseDragged, pt(n[0] + (n[2] - n[0]) * f, n[1] + (n[3] - n[1]) * f))
    usleep(useconds_t(ms * 1000 / Double(steps)))
  }
  if hold > 0 { usleep(useconds_t(hold * 1000)) }
  post(.leftMouseUp, pt(n[2], n[3]))
default:
  print("usage: touch tap X Y | drag X1 Y1 X2 Y2 [ms] [holdMs] | longpress X Y [ms]"); exit(2)
}
