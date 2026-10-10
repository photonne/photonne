using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <summary>
    /// Data-only: carries each user's excluded-folder list over to the key's new
    /// name. No schema change, so this is hand-written — the scaffold came out
    /// empty.
    ///
    /// The setting predates the rename as "TimelinePrefs.ExcludedFolders", from
    /// when the timeline was the only surface that honoured it. Without this
    /// UPDATE the new key reads nothing, and every folder a user had excluded
    /// reappears across their timeline, memories and search without a word —
    /// which is the very bug the rename is part of fixing.
    /// </summary>
    public partial class RenameTimelinePrefsToDiscoveryPrefs : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            // Delete-then-update rather than a bare UPDATE: (OwnerId, Key) is the
            // primary key, so a user holding both rows would collide. The new key
            // can only be there already if a newer build wrote it, in which case
            // it's the fresher of the two and the old row is the stale one.
            migrationBuilder.Sql("""
                DELETE FROM "Settings" old
                WHERE old."Key" = 'TimelinePrefs.ExcludedFolders'
                  AND EXISTS (
                      SELECT 1 FROM "Settings" newer
                      WHERE newer."OwnerId" = old."OwnerId"
                        AND newer."Key" = 'DiscoveryPrefs.ExcludedFolders');
                """);

            migrationBuilder.Sql("""
                UPDATE "Settings"
                SET "Key" = 'DiscoveryPrefs.ExcludedFolders'
                WHERE "Key" = 'TimelinePrefs.ExcludedFolders';
                """);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            // Reversible on purpose: rolling the server back must not strand a
            // user's exclusions under a key the old build has never heard of.
            migrationBuilder.Sql("""
                DELETE FROM "Settings" recent
                WHERE recent."Key" = 'DiscoveryPrefs.ExcludedFolders'
                  AND EXISTS (
                      SELECT 1 FROM "Settings" older
                      WHERE older."OwnerId" = recent."OwnerId"
                        AND older."Key" = 'TimelinePrefs.ExcludedFolders');
                """);

            migrationBuilder.Sql("""
                UPDATE "Settings"
                SET "Key" = 'TimelinePrefs.ExcludedFolders'
                WHERE "Key" = 'DiscoveryPrefs.ExcludedFolders';
                """);
        }
    }
}
