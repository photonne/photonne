using System.IO.Compression;
using ImageMagick;
using Photonne.Server.Api.Features.Assets;
using Photonne.Server.Api.Shared.Services;
using Photonne.Server.Api.Tests.Fixtures;

namespace Photonne.Server.Api.Tests.Assets;

/// <summary>
/// The ZIP of a selection is written straight into the response. These write
/// it into a stream shaped like Kestrel's response body: forward-only, and
/// refusing synchronous writes. Files on disk, no database.
/// </summary>
public sealed class DownloadZipStreamingTests : IDisposable
{
    private readonly string _dir = Directory.CreateTempSubdirectory("photonne-zip-").FullName;

    public void Dispose() => Directory.Delete(_dir, recursive: true);

    [Fact]
    public async Task Writes_EveryFile_IntoAForwardOnlyStream()
    {
        var a = WriteFile("a.jpg", 200_000);
        var b = WriteFile("b.mp4", 1_000);

        var zip = await WriteZip([new(a, "a.jpg"), new(b, "b.mp4")], AssetDownloadFormat.Default);

        using var archive = new ZipArchive(new MemoryStream(zip), ZipArchiveMode.Read);
        Assert.Equal(["a.jpg", "b.mp4"], archive.Entries.Select(e => e.FullName));
        Assert.Equal(File.ReadAllBytes(a), ReadEntry(archive, "a.jpg"));
        Assert.Equal(File.ReadAllBytes(b), ReadEntry(archive, "b.mp4"));
    }

    [Fact]
    public async Task ABigFile_GoesOutInChunks()
    {
        var big = WriteFile("clip.mp4", 1_000_000);
        var output = new ResponseLikeStream();

        await DownloadZipEndpoint.WriteZipAsync(output, [new(big, "clip.mp4")], AssetDownloadFormat.Default, CancellationToken.None);

        // Never more than one read chunk (80 KB) plus the entry header at once:
        // the file isn't held whole on its way into the response.
        Assert.True(output.Writes > 10, $"{output.Writes} writes");
        Assert.True(output.LargestWrite <= 81920 + 1024, $"largest write {output.LargestWrite}");
    }

    [Fact]
    public async Task RepeatedNames_GetANumber()
    {
        var first = WriteFile("one/IMG_1.jpg", 10);
        var second = WriteFile("two/IMG_1.jpg", 20);

        var zip = await WriteZip([new(first, "IMG_1.jpg"), new(second, "img_1.jpg")], AssetDownloadFormat.Default);

        using var archive = new ZipArchive(new MemoryStream(zip), ZipArchiveMode.Read);
        Assert.Equal(["IMG_1.jpg", "img_1_1.jpg"], archive.Entries.Select(e => e.FullName));
    }

    [Fact]
    public async Task FileGoneFromDisk_IsLeftOut()
    {
        var kept = WriteFile("kept.jpg", 10);

        var zip = await WriteZip(
            [new(Path.Combine(_dir, "gone.jpg"), "gone.jpg"), new(kept, "kept.jpg")],
            AssetDownloadFormat.Default);

        using var archive = new ZipArchive(new MemoryStream(zip), ZipArchiveMode.Read);
        Assert.Equal(["kept.jpg"], archive.Entries.Select(e => e.FullName));
    }

    [Fact]
    public async Task Heic_AsJpeg_GoesInConverted()
    {
        var zip = await WriteZip([new(FixturePaths.Heic, "IMG_0001.HEIC")], AssetDownloadFormat.Jpeg);

        using var archive = new ZipArchive(new MemoryStream(zip), ZipArchiveMode.Read);
        var entry = Assert.Single(archive.Entries);
        Assert.Equal("IMG_0001.jpg", entry.FullName);
        using var image = new MagickImage(ReadEntry(archive, "IMG_0001.jpg"));
        Assert.Equal(MagickFormat.Jpeg, image.Format);
    }

    [Fact]
    public async Task Cancelling_StopsTheZip()
    {
        var file = WriteFile("a.jpg", 10);
        using var cts = new CancellationTokenSource();
        await cts.CancelAsync();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            DownloadZipEndpoint.WriteZipAsync(new ResponseLikeStream(), [new(file, "a.jpg")], AssetDownloadFormat.Default, cts.Token));
    }

    private string WriteFile(string relativePath, int size)
    {
        var path = Path.Combine(_dir, relativePath);
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var bytes = new byte[size];
        new Random(size).NextBytes(bytes);
        File.WriteAllBytes(path, bytes);
        return path;
    }

    private static async Task<byte[]> WriteZip(IReadOnlyList<ZipSource> files, AssetDownloadFormat format)
    {
        var output = new ResponseLikeStream();
        await DownloadZipEndpoint.WriteZipAsync(output, files, format, CancellationToken.None);
        return output.Written.ToArray();
    }

    private static byte[] ReadEntry(ZipArchive archive, string name)
    {
        using var stream = archive.GetEntry(name)!.Open();
        using var copy = new MemoryStream();
        stream.CopyTo(copy);
        return copy.ToArray();
    }

    /// <summary>Forward-only and async-only, like an HTTP response body.</summary>
    private sealed class ResponseLikeStream : Stream
    {
        public MemoryStream Written { get; } = new();
        public int Writes { get; private set; }
        public int LargestWrite { get; private set; }

        public override bool CanRead => false;
        public override bool CanSeek => false;
        public override bool CanWrite => true;
        public override long Length => throw new NotSupportedException();
        public override long Position
        {
            get => throw new NotSupportedException();
            set => throw new NotSupportedException();
        }

        public override void Write(byte[] buffer, int offset, int count) =>
            throw new InvalidOperationException("Synchronous writes are not allowed.");

        public override void Flush() =>
            throw new InvalidOperationException("Synchronous writes are not allowed.");

        public override async ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken ct = default)
        {
            Writes++;
            LargestWrite = Math.Max(LargestWrite, buffer.Length);
            await Written.WriteAsync(buffer, ct);
        }

        public override Task WriteAsync(byte[] buffer, int offset, int count, CancellationToken ct) =>
            WriteAsync(buffer.AsMemory(offset, count), ct).AsTask();

        public override Task FlushAsync(CancellationToken ct) => Task.CompletedTask;

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
    }
}
