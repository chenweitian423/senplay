#!/usr/bin/env swift
//
// Draws the app icon — a small whale in deep water — and writes every size both
// platforms need.
//
// The icon is generated rather than checked in as a source image so that the shape
// can be adjusted in one place and both platforms stay identical. Run it from the
// repository root:
//
//     swift tools/make-icon.swift
//
// Coordinates below are fractions of the canvas with the origin at the bottom left,
// which is how Core Graphics measures. Everything is a circle, an ellipse or a
// quadratic curve, because that is what still reads at 48 px.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// MARK: - palette

private let colDeep = CGColor(red: 0.024, green: 0.153, blue: 0.267, alpha: 1)
private let colSea = CGColor(red: 0.055, green: 0.463, blue: 0.749, alpha: 1)
private let colBody = CGColor(red: 0.949, green: 0.988, blue: 1.000, alpha: 1)
private let colBelly = CGColor(red: 0.678, green: 0.851, blue: 0.941, alpha: 1)
private let colInk = CGColor(red: 0.024, green: 0.153, blue: 0.267, alpha: 1)
private let colSpray = CGColor(red: 1, green: 1, blue: 1, alpha: 0.90)

private func at(_ x: CGFloat, _ y: CGFloat, _ s: CGFloat) -> CGPoint {
    CGPoint(x: x * s, y: y * s)
}

// MARK: - pieces

/// Deep water with one broad light shaft, so the icon has a direction even at 48 px.
private func drawWater(_ ctx: CGContext, size s: CGFloat) {
    let space = CGColorSpaceCreateDeviceRGB()
    guard let gradient = CGGradient(colorsSpace: space,
                                    colors: [colDeep, colSea] as CFArray,
                                    locations: [0, 1]) else { return }
    ctx.saveGState()
    ctx.drawLinearGradient(gradient,
                           start: CGPoint(x: s * 0.5, y: 0),
                           end: CGPoint(x: s * 0.5, y: s),
                           options: [])
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.07))
    ctx.fillEllipse(in: CGRect(x: -s * 0.10, y: s * 0.66, width: s * 0.95, height: s * 0.42))
    ctx.restoreGState()
}

private func bodyPath(_ s: CGFloat) -> CGPath {
    let path = CGMutablePath()
    path.addEllipse(in: CGRect(x: (0.215) * s, y: (0.285) * s,
                               width: (0.49) * s, height: (0.35) * s))
    return path
}

/// Two flukes with a notch between them. The path starts and ends well inside the
/// body: where it meets the ellipse the two fills have to overlap, or a sliver of
/// background shows through at the join.
private func tailPath(_ s: CGFloat) -> CGPath {
    let path = CGMutablePath()
    path.move(to: at(0.600, 0.500, s))
    path.addQuadCurve(to: at(0.880, 0.640, s), control: at(0.760, 0.610, s))
    path.addQuadCurve(to: at(0.740, 0.460, s), control: at(0.812, 0.515, s))
    path.addQuadCurve(to: at(0.880, 0.280, s), control: at(0.812, 0.400, s))
    path.addQuadCurve(to: at(0.600, 0.420, s), control: at(0.760, 0.310, s))
    path.closeSubpath()
    return path
}

/// The pectoral fin: a small tilted ellipse, which is what it looks like from the
/// side. A pointed shape here reads as a leg at icon sizes.
private func drawFlipper(_ ctx: CGContext, size s: CGFloat) {
    ctx.saveGState()
    ctx.translateBy(x: 0.470 * s, y: 0.312 * s)
    ctx.rotate(by: -0.42)
    ctx.setFillColor(colBody)
    ctx.fillEllipse(in: CGRect(x: -0.082 * s, y: -0.030 * s,
                               width: 0.164 * s, height: 0.060 * s))
    ctx.restoreGState()
}

private func drawWhale(_ ctx: CGContext, size s: CGFloat) {
    ctx.saveGState()

    // Body and flukes are filled as separate paths. Added to one path they would be
    // wound in opposite directions, and the non-zero rule cancels the overlap —
    // which is what left a sliver of water between the body and the tail.
    ctx.setFillColor(colBody)
    ctx.addPath(bodyPath(s))
    ctx.fillPath()
    ctx.addPath(tailPath(s))
    ctx.fillPath()

    // Belly: the lower part of the body, clipped to the body outline.
    ctx.saveGState()
    ctx.addPath(bodyPath(s))
    ctx.clip()
    ctx.setFillColor(colBelly)
    ctx.fillEllipse(in: CGRect(x: 0.180 * s, y: 0.180 * s, width: 0.56 * s, height: 0.28 * s))
    // Throat grooves, the thing that makes a whale a whale rather than a fish.
    ctx.setStrokeColor(CGColor(red: 0.024, green: 0.153, blue: 0.267, alpha: 0.20))
    ctx.setLineWidth(0.011 * s)
    ctx.setLineCap(.round)
    for x in [0.302, 0.348, 0.394] as [CGFloat] {
        ctx.move(to: at(x, 0.400, s))
        ctx.addLine(to: at(x - 0.012, 0.316, s))
    }
    ctx.strokePath()
    ctx.restoreGState()

    drawFlipper(ctx, size: s)

    // Eye, with a highlight so it does not read as a hole.
    ctx.setFillColor(colInk)
    ctx.fillEllipse(in: CGRect(x: 0.310 * s, y: 0.478 * s, width: 0.056 * s, height: 0.056 * s))
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.95))
    ctx.fillEllipse(in: CGRect(x: 0.332 * s, y: 0.504 * s, width: 0.019 * s, height: 0.019 * s))

    // Mouth: one short curve, angled slightly up at the front.
    ctx.setStrokeColor(colInk)
    ctx.setLineWidth(0.013 * s)
    ctx.setLineCap(.round)
    ctx.move(to: at(0.240, 0.432, s))
    ctx.addQuadCurve(to: at(0.400, 0.418, s), control: at(0.320, 0.392, s))
    ctx.strokePath()

    // Spout: a rising column with two smaller drops.
    ctx.setFillColor(colSpray)
    ctx.fillEllipse(in: CGRect(x: 0.372 * s, y: 0.700 * s, width: 0.056 * s, height: 0.072 * s))
    ctx.fillEllipse(in: CGRect(x: 0.330 * s, y: 0.680 * s, width: 0.034 * s, height: 0.034 * s))
    ctx.fillEllipse(in: CGRect(x: 0.438 * s, y: 0.684 * s, width: 0.034 * s, height: 0.034 * s))

    ctx.restoreGState()
}

// MARK: - rendering

private func render(size: Int, whaleScale: CGFloat, water: Bool) -> CGImage? {
    let s = CGFloat(size)
    let space = CGColorSpaceCreateDeviceRGB()
    guard let ctx = CGContext(data: nil, width: size, height: size,
                              bitsPerComponent: 8, bytesPerRow: 0, space: space,
                              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
        return nil
    }
    ctx.interpolationQuality = .high
    ctx.setAllowsAntialiasing(true)

    if water { drawWater(ctx, size: s) }

    // The launcher foreground is drawn on its own layer, so the whale sits inside
    // the adaptive icon's safe area instead of against the full canvas.
    if whaleScale < 1 {
        let inset = (1 - whaleScale) / 2 * s
        ctx.translateBy(x: inset, y: inset)
        ctx.scaleBy(x: whaleScale, y: whaleScale)
    }
    drawWhale(ctx, size: s)
    return ctx.makeImage()
}

private func write(_ image: CGImage, to path: String) throws {
    let url = URL(fileURLWithPath: path)
    try FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                            withIntermediateDirectories: true)
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil) else {
        throw NSError(domain: "icon", code: 1)
    }
    CGImageDestinationAddImage(dest, image, nil)
    guard CGImageDestinationFinalize(dest) else {
        throw NSError(domain: "icon", code: 2)
    }
}

// MARK: - outputs

let root = FileManager.default.currentDirectoryPath
let iosDir = "\(root)/ios/SenPlay/Assets.xcassets/AppIcon.appiconset"
let resDir = "\(root)/android/app/src/main/res"

// iOS: one 1024 px master. The system does the rounding and the downscaling.
if let image = render(size: 1024, whaleScale: 1, water: true) {
    try write(image, to: "\(iosDir)/AppIcon-1024.png")
    print("ios  AppIcon-1024.png")
}

// Android legacy launcher icons, one per density bucket.
let legacy: [(String, Int)] = [
    ("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192),
]
for (bucket, px) in legacy {
    if let image = render(size: px, whaleScale: 1, water: true) {
        try write(image, to: "\(resDir)/mipmap-\(bucket)/ic_launcher.png")
        print("a    mipmap-\(bucket)/ic_launcher.png  \(px)px")
    }
}

// Android adaptive icon: the whale on its own transparent layer, scaled into the
// 66/108 dp safe area, plus the water as a separate background layer. Both layers
// cover the full canvas because the launcher crops them to its own mask.
for (bucket, px) in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216),
                     ("xxhdpi", 324), ("xxxhdpi", 432)] {
    if let image = render(size: px, whaleScale: 0.611, water: false) {
        try write(image, to: "\(resDir)/mipmap-\(bucket)/ic_launcher_foreground.png")
        print("a    mipmap-\(bucket)/ic_launcher_foreground.png  \(px)px")
    }
    if let image = render(size: px, whaleScale: 1, water: true) {
        try write(image, to: "\(resDir)/mipmap-\(bucket)/ic_launcher_background.png")
        print("a    mipmap-\(bucket)/ic_launcher_background.png  \(px)px")
    }
}

print("done")
