using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddAssetPrefixToMlTables : Migration
    {
        // Aligns the three pure ML output tables with the Asset* prefix
        // convention already used for AssetExif / AssetThumbnail / AssetTag /
        // AssetMlJob — tables that have only an AssetId FK and no user state:
        //   DetectedObjects     → AssetDetectedObjects
        //   ClassifiedScenes    → AssetClassifiedScenes
        //   RecognizedTextLines → AssetRecognizedTextLines
        //
        // Faces is intentionally NOT prefixed: it has a Person FK and stores
        // user decisions, so it stays a first-class domain entity.

        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            // --- DetectedObjects → AssetDetectedObjects ---
            migrationBuilder.RenameTable(
                name: "DetectedObjects",
                newName: "AssetDetectedObjects");

            migrationBuilder.RenameIndex(
                name: "IX_DetectedObjects_AssetId",
                table: "AssetDetectedObjects",
                newName: "IX_AssetDetectedObjects_AssetId");

            migrationBuilder.RenameIndex(
                name: "IX_DetectedObjects_Label",
                table: "AssetDetectedObjects",
                newName: "IX_AssetDetectedObjects_Label");

            migrationBuilder.RenameIndex(
                name: "IX_DetectedObjects_AssetId_Label",
                table: "AssetDetectedObjects",
                newName: "IX_AssetDetectedObjects_AssetId_Label");

            migrationBuilder.Sql(@"ALTER TABLE ""AssetDetectedObjects"" RENAME CONSTRAINT ""PK_DetectedObjects"" TO ""PK_AssetDetectedObjects"";");
            migrationBuilder.Sql(@"ALTER TABLE ""AssetDetectedObjects"" RENAME CONSTRAINT ""FK_DetectedObjects_Assets_AssetId"" TO ""FK_AssetDetectedObjects_Assets_AssetId"";");

            // --- ClassifiedScenes → AssetClassifiedScenes ---
            migrationBuilder.RenameTable(
                name: "ClassifiedScenes",
                newName: "AssetClassifiedScenes");

            migrationBuilder.RenameIndex(
                name: "IX_ClassifiedScenes_AssetId",
                table: "AssetClassifiedScenes",
                newName: "IX_AssetClassifiedScenes_AssetId");

            migrationBuilder.RenameIndex(
                name: "IX_ClassifiedScenes_Label",
                table: "AssetClassifiedScenes",
                newName: "IX_AssetClassifiedScenes_Label");

            migrationBuilder.RenameIndex(
                name: "IX_ClassifiedScenes_AssetId_Label",
                table: "AssetClassifiedScenes",
                newName: "IX_AssetClassifiedScenes_AssetId_Label");

            migrationBuilder.Sql(@"ALTER TABLE ""AssetClassifiedScenes"" RENAME CONSTRAINT ""PK_ClassifiedScenes"" TO ""PK_AssetClassifiedScenes"";");
            migrationBuilder.Sql(@"ALTER TABLE ""AssetClassifiedScenes"" RENAME CONSTRAINT ""FK_ClassifiedScenes_Assets_AssetId"" TO ""FK_AssetClassifiedScenes_Assets_AssetId"";");

            // --- RecognizedTextLines → AssetRecognizedTextLines ---
            // Drop and recreate the GIN expression index because EF Core can't
            // rename expression indexes (the underlying index is built via raw
            // SQL on to_tsvector('simple', "Text")).
            migrationBuilder.Sql(@"DROP INDEX IF EXISTS ""IX_RecognizedTextLines_TextSearch"";");

            migrationBuilder.RenameTable(
                name: "RecognizedTextLines",
                newName: "AssetRecognizedTextLines");

            migrationBuilder.RenameIndex(
                name: "IX_RecognizedTextLines_AssetId",
                table: "AssetRecognizedTextLines",
                newName: "IX_AssetRecognizedTextLines_AssetId");

            migrationBuilder.RenameIndex(
                name: "IX_RecognizedTextLines_AssetId_LineIndex",
                table: "AssetRecognizedTextLines",
                newName: "IX_AssetRecognizedTextLines_AssetId_LineIndex");

            migrationBuilder.Sql(@"ALTER TABLE ""AssetRecognizedTextLines"" RENAME CONSTRAINT ""PK_RecognizedTextLines"" TO ""PK_AssetRecognizedTextLines"";");
            migrationBuilder.Sql(@"ALTER TABLE ""AssetRecognizedTextLines"" RENAME CONSTRAINT ""FK_RecognizedTextLines_Assets_AssetId"" TO ""FK_AssetRecognizedTextLines_Assets_AssetId"";");

            migrationBuilder.Sql(
                "CREATE INDEX \"IX_AssetRecognizedTextLines_TextSearch\" " +
                "ON \"AssetRecognizedTextLines\" USING gin (to_tsvector('simple', \"Text\"));");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            // --- AssetRecognizedTextLines → RecognizedTextLines ---
            migrationBuilder.Sql(@"DROP INDEX IF EXISTS ""IX_AssetRecognizedTextLines_TextSearch"";");

            migrationBuilder.Sql(@"ALTER TABLE ""AssetRecognizedTextLines"" RENAME CONSTRAINT ""FK_AssetRecognizedTextLines_Assets_AssetId"" TO ""FK_RecognizedTextLines_Assets_AssetId"";");
            migrationBuilder.Sql(@"ALTER TABLE ""AssetRecognizedTextLines"" RENAME CONSTRAINT ""PK_AssetRecognizedTextLines"" TO ""PK_RecognizedTextLines"";");

            migrationBuilder.RenameIndex(
                name: "IX_AssetRecognizedTextLines_AssetId_LineIndex",
                table: "AssetRecognizedTextLines",
                newName: "IX_RecognizedTextLines_AssetId_LineIndex");

            migrationBuilder.RenameIndex(
                name: "IX_AssetRecognizedTextLines_AssetId",
                table: "AssetRecognizedTextLines",
                newName: "IX_RecognizedTextLines_AssetId");

            migrationBuilder.RenameTable(
                name: "AssetRecognizedTextLines",
                newName: "RecognizedTextLines");

            migrationBuilder.Sql(
                "CREATE INDEX \"IX_RecognizedTextLines_TextSearch\" " +
                "ON \"RecognizedTextLines\" USING gin (to_tsvector('simple', \"Text\"));");

            // --- AssetClassifiedScenes → ClassifiedScenes ---
            migrationBuilder.Sql(@"ALTER TABLE ""AssetClassifiedScenes"" RENAME CONSTRAINT ""FK_AssetClassifiedScenes_Assets_AssetId"" TO ""FK_ClassifiedScenes_Assets_AssetId"";");
            migrationBuilder.Sql(@"ALTER TABLE ""AssetClassifiedScenes"" RENAME CONSTRAINT ""PK_AssetClassifiedScenes"" TO ""PK_ClassifiedScenes"";");

            migrationBuilder.RenameIndex(
                name: "IX_AssetClassifiedScenes_AssetId_Label",
                table: "AssetClassifiedScenes",
                newName: "IX_ClassifiedScenes_AssetId_Label");

            migrationBuilder.RenameIndex(
                name: "IX_AssetClassifiedScenes_Label",
                table: "AssetClassifiedScenes",
                newName: "IX_ClassifiedScenes_Label");

            migrationBuilder.RenameIndex(
                name: "IX_AssetClassifiedScenes_AssetId",
                table: "AssetClassifiedScenes",
                newName: "IX_ClassifiedScenes_AssetId");

            migrationBuilder.RenameTable(
                name: "AssetClassifiedScenes",
                newName: "ClassifiedScenes");

            // --- AssetDetectedObjects → DetectedObjects ---
            migrationBuilder.Sql(@"ALTER TABLE ""AssetDetectedObjects"" RENAME CONSTRAINT ""FK_AssetDetectedObjects_Assets_AssetId"" TO ""FK_DetectedObjects_Assets_AssetId"";");
            migrationBuilder.Sql(@"ALTER TABLE ""AssetDetectedObjects"" RENAME CONSTRAINT ""PK_AssetDetectedObjects"" TO ""PK_DetectedObjects"";");

            migrationBuilder.RenameIndex(
                name: "IX_AssetDetectedObjects_AssetId_Label",
                table: "AssetDetectedObjects",
                newName: "IX_DetectedObjects_AssetId_Label");

            migrationBuilder.RenameIndex(
                name: "IX_AssetDetectedObjects_Label",
                table: "AssetDetectedObjects",
                newName: "IX_DetectedObjects_Label");

            migrationBuilder.RenameIndex(
                name: "IX_AssetDetectedObjects_AssetId",
                table: "AssetDetectedObjects",
                newName: "IX_DetectedObjects_AssetId");

            migrationBuilder.RenameTable(
                name: "AssetDetectedObjects",
                newName: "DetectedObjects");
        }
    }
}
