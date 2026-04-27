using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace Photonne.Server.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddFaceSuggestions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<float>(
                name: "SuggestedDistance",
                table: "Faces",
                type: "real",
                nullable: true);

            migrationBuilder.AddColumn<System.Guid>(
                name: "SuggestedPersonId",
                table: "Faces",
                type: "uuid",
                nullable: true);

            migrationBuilder.CreateIndex(
                name: "IX_Faces_SuggestedPersonId",
                table: "Faces",
                column: "SuggestedPersonId");

            migrationBuilder.AddForeignKey(
                name: "FK_Faces_People_SuggestedPersonId",
                table: "Faces",
                column: "SuggestedPersonId",
                principalTable: "People",
                principalColumn: "Id",
                onDelete: ReferentialAction.SetNull);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_Faces_People_SuggestedPersonId",
                table: "Faces");

            migrationBuilder.DropIndex(
                name: "IX_Faces_SuggestedPersonId",
                table: "Faces");

            migrationBuilder.DropColumn(
                name: "SuggestedPersonId",
                table: "Faces");

            migrationBuilder.DropColumn(
                name: "SuggestedDistance",
                table: "Faces");
        }
    }
}
