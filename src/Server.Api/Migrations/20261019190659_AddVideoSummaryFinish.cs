using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddVideoSummaryFinish : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<Guid>(
                name: "MusicId",
                table: "VideoSummaries",
                type: "uuid",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "MusicName",
                table: "VideoSummaries",
                type: "character varying(200)",
                maxLength: 200,
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "ShowTitles",
                table: "VideoSummaries",
                type: "boolean",
                nullable: false,
                defaultValue: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "MusicId",
                table: "VideoSummaries");

            migrationBuilder.DropColumn(
                name: "MusicName",
                table: "VideoSummaries");

            migrationBuilder.DropColumn(
                name: "ShowTitles",
                table: "VideoSummaries");
        }
    }
}
