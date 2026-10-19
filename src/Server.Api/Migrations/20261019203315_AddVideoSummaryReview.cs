using System;
using System.Collections.Generic;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddVideoSummaryReview : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<List<Guid>>(
                name: "PinnedAssetIds",
                table: "VideoSummaries",
                type: "uuid[]",
                nullable: false,
                defaultValueSql: "'{}'::uuid[]");

            migrationBuilder.AddColumn<string>(
                name: "Plan",
                table: "VideoSummaries",
                type: "jsonb",
                nullable: true);

            migrationBuilder.AddColumn<bool>(
                name: "PlanApproved",
                table: "VideoSummaries",
                type: "boolean",
                nullable: false,
                defaultValue: false);

            migrationBuilder.AddColumn<bool>(
                name: "Review",
                table: "VideoSummaries",
                type: "boolean",
                nullable: false,
                defaultValue: false);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "PinnedAssetIds",
                table: "VideoSummaries");

            migrationBuilder.DropColumn(
                name: "Plan",
                table: "VideoSummaries");

            migrationBuilder.DropColumn(
                name: "PlanApproved",
                table: "VideoSummaries");

            migrationBuilder.DropColumn(
                name: "Review",
                table: "VideoSummaries");
        }
    }
}
