using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class RenameMlEntities : Migration
    {
        // Renames the ML output tables and timestamp columns to match the
        // unified naming convention:
        //   ObjectDetections      → DetectedObjects
        //   SceneClassifications  → ClassifiedScenes
        //   ExtractedTexts        → RecognizedTextLines
        //   FaceDetectionCompletedAt     → FaceRecognitionCompletedAt
        //   ObjectRecognitionCompletedAt → ObjectDetectionCompletedAt
        //
        // Tables that already follow the rule (Faces, SceneClassificationCompletedAt,
        // TextRecognitionCompletedAt) are left untouched. The MlJobType enum reuses
        // the same integer ordinals so jobs already in flight keep their meaning.

        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            // --- Asset timestamp columns ---
            migrationBuilder.RenameColumn(
                name: "FaceDetectionCompletedAt",
                table: "Assets",
                newName: "FaceRecognitionCompletedAt");

            migrationBuilder.RenameColumn(
                name: "ObjectRecognitionCompletedAt",
                table: "Assets",
                newName: "ObjectDetectionCompletedAt");

            // --- ObjectDetections → DetectedObjects ---
            migrationBuilder.RenameTable(
                name: "ObjectDetections",
                newName: "DetectedObjects");

            migrationBuilder.RenameIndex(
                name: "IX_ObjectDetections_AssetId",
                table: "DetectedObjects",
                newName: "IX_DetectedObjects_AssetId");

            migrationBuilder.RenameIndex(
                name: "IX_ObjectDetections_Label",
                table: "DetectedObjects",
                newName: "IX_DetectedObjects_Label");

            migrationBuilder.RenameIndex(
                name: "IX_ObjectDetections_AssetId_Label",
                table: "DetectedObjects",
                newName: "IX_DetectedObjects_AssetId_Label");

            // PK and FK constraint names are not renamed automatically by
            // RenameTable in PostgreSQL, so do it explicitly to keep the
            // catalog tidy and aligned with EF's expected snapshot names.
            migrationBuilder.Sql(@"ALTER TABLE ""DetectedObjects"" RENAME CONSTRAINT ""PK_ObjectDetections"" TO ""PK_DetectedObjects"";");
            migrationBuilder.Sql(@"ALTER TABLE ""DetectedObjects"" RENAME CONSTRAINT ""FK_ObjectDetections_Assets_AssetId"" TO ""FK_DetectedObjects_Assets_AssetId"";");

            // --- SceneClassifications → ClassifiedScenes ---
            migrationBuilder.RenameTable(
                name: "SceneClassifications",
                newName: "ClassifiedScenes");

            migrationBuilder.RenameIndex(
                name: "IX_SceneClassifications_AssetId",
                table: "ClassifiedScenes",
                newName: "IX_ClassifiedScenes_AssetId");

            migrationBuilder.RenameIndex(
                name: "IX_SceneClassifications_Label",
                table: "ClassifiedScenes",
                newName: "IX_ClassifiedScenes_Label");

            migrationBuilder.RenameIndex(
                name: "IX_SceneClassifications_AssetId_Label",
                table: "ClassifiedScenes",
                newName: "IX_ClassifiedScenes_AssetId_Label");

            migrationBuilder.Sql(@"ALTER TABLE ""ClassifiedScenes"" RENAME CONSTRAINT ""PK_SceneClassifications"" TO ""PK_ClassifiedScenes"";");
            migrationBuilder.Sql(@"ALTER TABLE ""ClassifiedScenes"" RENAME CONSTRAINT ""FK_SceneClassifications_Assets_AssetId"" TO ""FK_ClassifiedScenes_Assets_AssetId"";");

            // --- ExtractedTexts → RecognizedTextLines ---
            // The FT GIN index has to be dropped and recreated because it was
            // built via raw SQL (EF Core can't rename expression indexes) and
            // its name is referenced in code (search/labels endpoints don't
            // depend on the name, but keeping it aligned with the table avoids
            // surprises during ops/maintenance).
            migrationBuilder.Sql(@"DROP INDEX IF EXISTS ""IX_ExtractedTexts_TextSearch"";");

            migrationBuilder.RenameTable(
                name: "ExtractedTexts",
                newName: "RecognizedTextLines");

            migrationBuilder.RenameIndex(
                name: "IX_ExtractedTexts_AssetId",
                table: "RecognizedTextLines",
                newName: "IX_RecognizedTextLines_AssetId");

            migrationBuilder.RenameIndex(
                name: "IX_ExtractedTexts_AssetId_LineIndex",
                table: "RecognizedTextLines",
                newName: "IX_RecognizedTextLines_AssetId_LineIndex");

            migrationBuilder.Sql(@"ALTER TABLE ""RecognizedTextLines"" RENAME CONSTRAINT ""PK_ExtractedTexts"" TO ""PK_RecognizedTextLines"";");
            migrationBuilder.Sql(@"ALTER TABLE ""RecognizedTextLines"" RENAME CONSTRAINT ""FK_ExtractedTexts_Assets_AssetId"" TO ""FK_RecognizedTextLines_Assets_AssetId"";");

            migrationBuilder.Sql(
                "CREATE INDEX \"IX_RecognizedTextLines_TextSearch\" " +
                "ON \"RecognizedTextLines\" USING gin (to_tsvector('simple', \"Text\"));");

            // --- Settings table data fix: runtime Enabled key for object pipeline ---
            // The "ObjectRecognition.Enabled" runtime override used to be read by
            // ObjectDetectionService; the pipeline is now ObjectDetection. Migrate
            // any user-set value across so admins don't lose their toggle.
            migrationBuilder.Sql(@"
                UPDATE ""Settings""
                SET ""Key"" = 'ObjectDetection.Enabled'
                WHERE ""Key"" = 'ObjectRecognition.Enabled'
                  AND NOT EXISTS (
                      SELECT 1 FROM ""Settings"" s2
                      WHERE s2.""Key"" = 'ObjectDetection.Enabled'
                        AND s2.""OwnerId"" = ""Settings"".""OwnerId""
                  );
                DELETE FROM ""Settings"" WHERE ""Key"" = 'ObjectRecognition.Enabled';
            ");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.Sql(@"
                UPDATE ""Settings""
                SET ""Key"" = 'ObjectRecognition.Enabled'
                WHERE ""Key"" = 'ObjectDetection.Enabled'
                  AND NOT EXISTS (
                      SELECT 1 FROM ""Settings"" s2
                      WHERE s2.""Key"" = 'ObjectRecognition.Enabled'
                        AND s2.""OwnerId"" = ""Settings"".""OwnerId""
                  );
                DELETE FROM ""Settings"" WHERE ""Key"" = 'ObjectDetection.Enabled';
            ");

            // --- RecognizedTextLines → ExtractedTexts ---
            migrationBuilder.Sql(@"DROP INDEX IF EXISTS ""IX_RecognizedTextLines_TextSearch"";");

            migrationBuilder.Sql(@"ALTER TABLE ""RecognizedTextLines"" RENAME CONSTRAINT ""FK_RecognizedTextLines_Assets_AssetId"" TO ""FK_ExtractedTexts_Assets_AssetId"";");
            migrationBuilder.Sql(@"ALTER TABLE ""RecognizedTextLines"" RENAME CONSTRAINT ""PK_RecognizedTextLines"" TO ""PK_ExtractedTexts"";");

            migrationBuilder.RenameIndex(
                name: "IX_RecognizedTextLines_AssetId_LineIndex",
                table: "RecognizedTextLines",
                newName: "IX_ExtractedTexts_AssetId_LineIndex");

            migrationBuilder.RenameIndex(
                name: "IX_RecognizedTextLines_AssetId",
                table: "RecognizedTextLines",
                newName: "IX_ExtractedTexts_AssetId");

            migrationBuilder.RenameTable(
                name: "RecognizedTextLines",
                newName: "ExtractedTexts");

            migrationBuilder.Sql(
                "CREATE INDEX \"IX_ExtractedTexts_TextSearch\" " +
                "ON \"ExtractedTexts\" USING gin (to_tsvector('simple', \"Text\"));");

            // --- ClassifiedScenes → SceneClassifications ---
            migrationBuilder.Sql(@"ALTER TABLE ""ClassifiedScenes"" RENAME CONSTRAINT ""FK_ClassifiedScenes_Assets_AssetId"" TO ""FK_SceneClassifications_Assets_AssetId"";");
            migrationBuilder.Sql(@"ALTER TABLE ""ClassifiedScenes"" RENAME CONSTRAINT ""PK_ClassifiedScenes"" TO ""PK_SceneClassifications"";");

            migrationBuilder.RenameIndex(
                name: "IX_ClassifiedScenes_AssetId_Label",
                table: "ClassifiedScenes",
                newName: "IX_SceneClassifications_AssetId_Label");

            migrationBuilder.RenameIndex(
                name: "IX_ClassifiedScenes_Label",
                table: "ClassifiedScenes",
                newName: "IX_SceneClassifications_Label");

            migrationBuilder.RenameIndex(
                name: "IX_ClassifiedScenes_AssetId",
                table: "ClassifiedScenes",
                newName: "IX_SceneClassifications_AssetId");

            migrationBuilder.RenameTable(
                name: "ClassifiedScenes",
                newName: "SceneClassifications");

            // --- DetectedObjects → ObjectDetections ---
            migrationBuilder.Sql(@"ALTER TABLE ""DetectedObjects"" RENAME CONSTRAINT ""FK_DetectedObjects_Assets_AssetId"" TO ""FK_ObjectDetections_Assets_AssetId"";");
            migrationBuilder.Sql(@"ALTER TABLE ""DetectedObjects"" RENAME CONSTRAINT ""PK_DetectedObjects"" TO ""PK_ObjectDetections"";");

            migrationBuilder.RenameIndex(
                name: "IX_DetectedObjects_AssetId_Label",
                table: "DetectedObjects",
                newName: "IX_ObjectDetections_AssetId_Label");

            migrationBuilder.RenameIndex(
                name: "IX_DetectedObjects_Label",
                table: "DetectedObjects",
                newName: "IX_ObjectDetections_Label");

            migrationBuilder.RenameIndex(
                name: "IX_DetectedObjects_AssetId",
                table: "DetectedObjects",
                newName: "IX_ObjectDetections_AssetId");

            migrationBuilder.RenameTable(
                name: "DetectedObjects",
                newName: "ObjectDetections");

            // --- Asset timestamp columns ---
            migrationBuilder.RenameColumn(
                name: "ObjectDetectionCompletedAt",
                table: "Assets",
                newName: "ObjectRecognitionCompletedAt");

            migrationBuilder.RenameColumn(
                name: "FaceRecognitionCompletedAt",
                table: "Assets",
                newName: "FaceDetectionCompletedAt");
        }
    }
}
