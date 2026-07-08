using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddDeletedByUser : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<Guid>(
                name: "DeletedByUserId",
                table: "Assets",
                type: "uuid",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_Assets_DeletedAt",
                table: "Assets",
                column: "DeletedAt");

            migrationBuilder.CreateIndex(
                name: "IX_Assets_DeletedByUserId",
                table: "Assets",
                column: "DeletedByUserId");

            migrationBuilder.AddForeignKey(
                name: "FK_Assets_Users_DeletedByUserId",
                table: "Assets",
                column: "DeletedByUserId",
                principalTable: "Users",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_Assets_Users_DeletedByUserId",
                table: "Assets");

            migrationBuilder.DropIndex(
                name: "IX_Assets_DeletedAt",
                table: "Assets");

            migrationBuilder.DropIndex(
                name: "IX_Assets_DeletedByUserId",
                table: "Assets");

            migrationBuilder.DropColumn(
                name: "DeletedByUserId",
                table: "Assets");
        }
    }
}
