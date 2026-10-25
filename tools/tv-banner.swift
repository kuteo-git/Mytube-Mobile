// Draws the Android TV launcher banner, 320×180 at xhdpi, from the launcher mark.
//
//   swift tools/tv-banner.swift composeApp/src/androidMain/res/drawable-xhdpi/tv_banner.png
//
// A script rather than an asset made by hand, for the reason the adaptive icon
// is a vector: the mark lives in ic_launcher_foreground.xml, and the next time
// it changes this is one command rather than a lost source file.

import AppKit

let w = 320, h = 180
let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h, bitsPerSample: 8,
    samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
NSColor(red: 0x0F/255.0, green: 0x0F/255.0, blue: 0x0F/255.0, alpha: 1).setFill()
NSRect(x: 0, y: 0, width: w, height: h).fill()

// The launcher's mark (ic_launcher_foreground.xml, 32-unit viewport), flipped for AppKit's y-up.
let scale: CGFloat = 2.2
let markW: CGFloat = 32 * scale
let title = NSAttributedString(string: "Mytube", attributes: [
    .font: NSFont.systemFont(ofSize: 40, weight: .semibold),
    .foregroundColor: NSColor(red: 0xF1/255.0, green: 0xF1/255.0, blue: 0xF1/255.0, alpha: 1),
])
let gap: CGFloat = 12
let total = markW + gap + title.size().width
let left = (CGFloat(w) - total) / 2
let ctx = NSGraphicsContext.current!.cgContext
ctx.saveGState()
ctx.translateBy(x: left, y: (CGFloat(h) + markW) / 2)
ctx.scaleBy(x: scale, y: -scale)
let house = CGMutablePath()
house.move(to: CGPoint(x: 16, y: 3.5))
house.addLine(to: CGPoint(x: 30, y: 14))
house.addLine(to: CGPoint(x: 30.6, y: 15.1))
house.addLine(to: CGPoint(x: 30.6, y: 27))
house.addQuadCurve(to: CGPoint(x: 28, y: 29.6), control: CGPoint(x: 30.6, y: 29.6))
house.addLine(to: CGPoint(x: 4, y: 29.6))
house.addQuadCurve(to: CGPoint(x: 1.4, y: 27), control: CGPoint(x: 1.4, y: 29.6))
house.addLine(to: CGPoint(x: 1.4, y: 15.1))
house.addLine(to: CGPoint(x: 2, y: 14))
house.closeSubpath()
ctx.setFillColor(NSColor(red: 1, green: 0, blue: 0, alpha: 1).cgColor)
ctx.addPath(house); ctx.fillPath()
let play = CGMutablePath()
play.move(to: CGPoint(x: 13, y: 15.5)); play.addLine(to: CGPoint(x: 21, y: 20)); play.addLine(to: CGPoint(x: 13, y: 24.5)); play.closeSubpath()
ctx.setFillColor(NSColor.white.cgColor)
ctx.addPath(play); ctx.fillPath()
ctx.restoreGState()
title.draw(at: NSPoint(x: left + markW + gap, y: (CGFloat(h) - title.size().height) / 2))
NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
