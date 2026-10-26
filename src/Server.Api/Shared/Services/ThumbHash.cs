namespace Photonne.Server.Api.Shared.Services;

/// <summary>
/// ThumbHash encoder (https://evanw.github.io/thumbhash/, MIT): a ~25-byte
/// sketch of a photo the clients paint, blurred, while the thumbnail loads.
/// A port of the reference rgbaToThumbHash; rounding is half-up like JS's
/// Math.round so the bytes match the reference exactly.
/// </summary>
public static class ThumbHash
{
    /// <summary>Encodes non-premultiplied RGBA pixels (at most 100×100) to a ThumbHash.</summary>
    public static byte[] FromRgba(int w, int h, ReadOnlySpan<byte> rgba)
    {
        if (w > 100 || h > 100) throw new ArgumentException($"{w}x{h} doesn't fit in 100x100");
        if (rgba.Length < w * h * 4) throw new ArgumentException("Not enough pixels");

        // The average colour, what transparent pixels are composited over.
        double avgR = 0, avgG = 0, avgB = 0, avgA = 0;
        for (int i = 0, j = 0; i < w * h; i++, j += 4)
        {
            var alpha = rgba[j + 3] / 255.0;
            avgR += alpha / 255.0 * rgba[j];
            avgG += alpha / 255.0 * rgba[j + 1];
            avgB += alpha / 255.0 * rgba[j + 2];
            avgA += alpha;
        }
        if (avgA > 0)
        {
            avgR /= avgA;
            avgG /= avgA;
            avgB /= avgA;
        }

        var hasAlpha = avgA < w * h;
        var lLimit = hasAlpha ? 5 : 7;
        var lx = Math.Max(1, Round(lLimit * w / (double)Math.Max(w, h)));
        var ly = Math.Max(1, Round(lLimit * h / (double)Math.Max(w, h)));
        var l = new double[w * h];
        var p = new double[w * h];
        var q = new double[w * h];
        var a = new double[w * h];

        // RGBA to LPQA: luminance, yellow-blue, red-green, alpha.
        for (int i = 0, j = 0; i < w * h; i++, j += 4)
        {
            var alpha = rgba[j + 3] / 255.0;
            var r = avgR * (1 - alpha) + alpha / 255.0 * rgba[j];
            var g = avgG * (1 - alpha) + alpha / 255.0 * rgba[j + 1];
            var b = avgB * (1 - alpha) + alpha / 255.0 * rgba[j + 2];
            l[i] = (r + g + b) / 3;
            p[i] = (r + g) / 2 - b;
            q[i] = r - g;
            a[i] = alpha;
        }

        var (lDc, lAc, lScale) = EncodeChannel(l, w, h, Math.Max(3, lx), Math.Max(3, ly));
        var (pDc, pAc, pScale) = EncodeChannel(p, w, h, 3, 3);
        var (qDc, qAc, qScale) = EncodeChannel(q, w, h, 3, 3);
        var (aDc, aAc, aScale) = hasAlpha ? EncodeChannel(a, w, h, 5, 5) : (0.0, new List<double>(), 0.0);

        var isLandscape = w > h;
        var header24 = Round(63 * lDc)
            | (Round(31.5 + 31.5 * pDc) << 6)
            | (Round(31.5 + 31.5 * qDc) << 12)
            | (Round(31 * lScale) << 18)
            | ((hasAlpha ? 1 : 0) << 23);
        var header16 = (isLandscape ? ly : lx)
            | (Round(63 * pScale) << 3)
            | (Round(63 * qScale) << 9)
            | ((isLandscape ? 1 : 0) << 15);

        var acStart = hasAlpha ? 6 : 5;
        var acs = hasAlpha ? new[] { lAc, pAc, qAc, aAc } : new[] { lAc, pAc, qAc };
        var acCount = acs.Sum(ac => ac.Count);
        var hash = new byte[acStart + (acCount + 1) / 2];
        hash[0] = (byte)(header24 & 255);
        hash[1] = (byte)((header24 >> 8) & 255);
        hash[2] = (byte)(header24 >> 16);
        hash[3] = (byte)(header16 & 255);
        hash[4] = (byte)(header16 >> 8);
        if (hasAlpha) hash[5] = (byte)(Round(15 * aDc) | (Round(15 * aScale) << 4));

        var acIndex = 0;
        foreach (var ac in acs)
        {
            foreach (var f in ac)
            {
                hash[acStart + (acIndex >> 1)] |= (byte)(Round(15 * f) << ((acIndex & 1) << 2));
                acIndex++;
            }
        }
        return hash;
    }

    /// <summary>The DCT of one channel: its constant term, normalised varying terms and their scale.</summary>
    private static (double Dc, List<double> Ac, double Scale) EncodeChannel(double[] channel, int w, int h, int nx, int ny)
    {
        double dc = 0, scale = 0;
        var ac = new List<double>();
        var fx = new double[w];
        for (var cy = 0; cy < ny; cy++)
        {
            for (var cx = 0; cx * ny < nx * (ny - cy); cx++)
            {
                double f = 0;
                for (var x = 0; x < w; x++)
                    fx[x] = Math.Cos(Math.PI / w * cx * (x + 0.5));
                for (var y = 0; y < h; y++)
                {
                    var fy = Math.Cos(Math.PI / h * cy * (y + 0.5));
                    for (var x = 0; x < w; x++)
                        f += channel[x + y * w] * fx[x] * fy;
                }
                f /= w * h;
                if (cx > 0 || cy > 0)
                {
                    ac.Add(f);
                    scale = Math.Max(scale, Math.Abs(f));
                }
                else
                {
                    dc = f;
                }
            }
        }
        if (scale > 0)
            for (var i = 0; i < ac.Count; i++)
                ac[i] = 0.5 + 0.5 / scale * ac[i];
        return (dc, ac, scale);
    }

    /// <summary>JavaScript's Math.round: halves go up, not to even.</summary>
    private static int Round(double value) => (int)Math.Floor(value + 0.5);
}
