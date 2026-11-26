Add-Type -AssemblyName System.Drawing

$size = 512
$bmp = New-Object System.Drawing.Bitmap($size, $size)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# Obsidian glass backdrop
$bgPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$bgPath.AddArc(0, 0, 96, 96, 180, 90)
$bgPath.AddArc($size - 96, 0, 96, 96, 270, 90)
$bgPath.AddArc($size - 96, $size - 96, 96, 96, 0, 90)
$bgPath.AddArc(0, $size - 96, 96, 96, 90, 90)
$bgPath.CloseFigure()
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.PointF(0, 0)),
    (New-Object System.Drawing.PointF($size, $size)),
    [System.Drawing.Color]::FromArgb(255, 22, 16, 34),
    [System.Drawing.Color]::FromArgb(255, 9, 7, 14))
$g.FillPath($bgBrush, $bgPath)

# Subtle inner rim
$rimPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(70, 200, 100, 255), 3)
$g.DrawPath($rimPen, $bgPath)

function Crescent([int]$ox, [int]$oy, [float]$scale, [System.Drawing.Brush]$brush) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.FillMode = [System.Drawing.Drawing2D.FillMode]::Alternate
    $path.AddEllipse(($ox + 92 * $scale), ($oy + 72 * $scale), (300 * $scale), (300 * $scale))
    $path.AddEllipse(($ox + 168 * $scale), ($oy + 48 * $scale), (300 * $scale), (300 * $scale))
    $g.FillPath($brush, $path)
}

# Layered aura
Crescent 0 0 1.10 (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(14, 200, 100, 255)))
Crescent 0 0 1.05 (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(26, 200, 100, 255)))
Crescent 0 0 1.00 (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(46, 200, 100, 255)))

$grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.PointF(90, 70)),
    (New-Object System.Drawing.PointF(400, 390)),
    [System.Drawing.Color]::FromArgb(255, 226, 178, 255),
    [System.Drawing.Color]::FromArgb(255, 150, 62, 236))
Crescent 0 0 0.96 $grad

# Illuminated X monogram
function DrawX([int]$alpha, [int]$width) {
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb($alpha, 245, 220, 255), $width)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawLine($pen, 214, 218, 322, 330)
    $g.DrawLine($pen, 322, 218, 214, 330)
}
DrawX 22 34
DrawX 48 22
DrawX 255 11

# Spark particles
foreach ($p in @(@(390, 118, 5), @(126, 372, 4), @(408, 342, 3), @(108, 150, 3))) {
    $r = [int]$p[2]
    $sb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 226, 178, 255))
    $g.FillEllipse($sb, ([int]$p[0]) - $r, ([int]$p[1]) - $r, $r * 2, $r * 2)
}

$bmp.Save((Join-Path $PSScriptRoot 'logo.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$icon = [System.Drawing.Icon]::FromHandle($bmp.GetHicon())
$fs = [System.IO.File]::Create((Join-Path $PSScriptRoot 'app.ico'))
$icon.Save($fs)
$fs.Close()
$g.Dispose()
$bmp.Dispose()
'Logo generated: logo.png + app.ico'
