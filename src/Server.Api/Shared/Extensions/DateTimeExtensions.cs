namespace Photonne.Server.Api.Shared.Extensions;

public static class DateTimeExtensions
{
    /// <summary>
    /// Drops what Postgres can't store (it keeps microseconds; .NET ticks are
    /// tenths of one). A value answered straight after saving then matches
    /// the one read back later.
    /// </summary>
    public static DateTime ToDatabasePrecision(this DateTime value) =>
        value.AddTicks(-(value.Ticks % TimeSpan.TicksPerMicrosecond));
}
