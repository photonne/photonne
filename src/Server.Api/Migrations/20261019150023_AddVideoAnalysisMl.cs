using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddVideoAnalysisMl : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "MlProfile",
                table: "AssetVideoAnalyses",
                type: "jsonb",
                nullable: true);

            migrationBuilder.AddColumn<int>(
                name: "MlVersion",
                table: "AssetVideoAnalyses",
                type: "integer",
                nullable: false,
                defaultValue: 0);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "MlProfile",
                table: "AssetVideoAnalyses");

            migrationBuilder.DropColumn(
                name: "MlVersion",
                table: "AssetVideoAnalyses");
        }
    }
}
