using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddPersonBranch : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<Guid>(
                name: "BranchFromPersonId",
                table: "People",
                type: "uuid",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_People_BranchFromPersonId",
                table: "People",
                column: "BranchFromPersonId");

            migrationBuilder.AddForeignKey(
                name: "FK_People_People_BranchFromPersonId",
                table: "People",
                column: "BranchFromPersonId",
                principalTable: "People",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_People_People_BranchFromPersonId",
                table: "People");

            migrationBuilder.DropIndex(
                name: "IX_People_BranchFromPersonId",
                table: "People");

            migrationBuilder.DropColumn(
                name: "BranchFromPersonId",
                table: "People");
        }
    }
}
