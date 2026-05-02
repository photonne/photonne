using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using Photonne.Server.Api.Features.Admin;
using Photonne.Server.Api.Shared.Data;
using Photonne.Server.Api.Shared.Interfaces;
using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services;

namespace Photonne.Server.Api.Features.People;

/// <summary>User-scoped face recognition backfill: any authenticated user can
/// enqueue FaceRecognition ML jobs for their own image assets
/// (<c>Asset.OwnerId == userId</c>) and read their own pending counts.
/// Mirrors <see cref="FaceRecognitionBackfillEndpoint"/> but without admin role
/// and with an owner filter; the global feature flag and thresholds remain
/// admin-managed.</summary>
public class UserFaceRecognitionBackfillEndpoint : IEndpoint
{
    public void MapEndpoint(IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/api/people/face-recognition")
            .WithTags("People")
            .RequireAuthorization();

        group.MapPost("/backfill", (
            [FromServices] ApplicationDbContext db,
            [FromServices] IMlJobService mlJobs,
            [FromServices] SettingsService settings,
            [FromBody] BackfillRequest? body,
            ClaimsPrincipal user,
            CancellationToken ct) =>
        {
            if (!ListPeopleEndpoint.TryGetUserId(user, out var userId))
                return Task.FromResult(Results.Unauthorized());
            return MlBackfillRunner.RunAsync(db, mlJobs, settings, MlJobType.FaceRecognition, body, ct, ownerScope: userId);
        });

        group.MapGet("/pending-count", (
            [FromServices] ApplicationDbContext db,
            ClaimsPrincipal user,
            CancellationToken ct) =>
        {
            if (!ListPeopleEndpoint.TryGetUserId(user, out var userId))
                return Task.FromResult(Results.Unauthorized());
            return MlBackfillRunner.GetPendingCountAsync(db, MlJobType.FaceRecognition, ct, ownerScope: userId);
        });
    }
}
