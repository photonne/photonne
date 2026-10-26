using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Tests.Shared;

/// <summary>
/// The encoder against the reference implementation (npm thumbhash 0.1.1):
/// the clients decode with it, so the bytes have to be the same. The pixels
/// are noise (Park–Miller, exact in both languages): a smooth gradient has
/// coefficients of exactly zero, where the last bit of cos() in V8 and .NET
/// can tip a 7.5 either way.
/// </summary>
public sealed class ThumbHashTests
{
    private static byte[] Noise(int w, int h, bool alpha)
    {
        long seed = 12345;
        byte Next()
        {
            seed = seed * 48271 % 2147483647;
            return (byte)(seed % 256);
        }

        var rgba = new byte[w * h * 4];
        for (var i = 0; i < w * h; i++)
        {
            rgba[i * 4] = Next();
            rgba[i * 4 + 1] = Next();
            rgba[i * 4 + 2] = Next();
            rgba[i * 4 + 3] = alpha ? Next() : (byte)255;
        }
        return rgba;
    }

    [Fact]
    public void Opaque_image_matches_the_reference()
    {
        Assert.Equal("ofcFJYRA7JqD6iiwmzaaOQl4iMB/", Convert.ToBase64String(ThumbHash.FromRgba(12, 9, Noise(12, 9, false))));
    }

    [Fact]
    public void Image_with_transparency_matches_the_reference()
    {
        Assert.Equal("HxiCFAQI2SB0OuWUewQcZrgPa4jMPbTGBA==", Convert.ToBase64String(ThumbHash.FromRgba(8, 11, Noise(8, 11, true))));
    }

    [Fact]
    public void Refuses_images_over_100_pixels()
    {
        Assert.Throws<ArgumentException>(() => ThumbHash.FromRgba(101, 10, new byte[101 * 10 * 4]));
    }
}
