using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Memories from what the object detector found. COCO's 80 classes are mostly
/// street furniture — "traffic light", "fire hydrant", "parking meter" — so only
/// a handful mean anything in a family library. These are those.
///
/// Pets get a lower bar than the rest: an animal that shows up in a dozen photos
/// a year lives with you, and that is exactly the sort of thing worth resurfacing.
/// </summary>
internal sealed class PetsAndFoodGenerator : ThemedMemoryGenerator
{
    /// <summary>Above YOLO's 0.25 storage floor (Ml.ObjectDetection.MinScore).
    /// Lower than the scene bar because detection over 80 classes is a far easier
    /// problem than scene classification over 365 — a 0.5 dog is a dog.</summary>
    private const float MinConfidence = 0.50f;

    public override MemoryKind Kind => MemoryKind.PetsAndFood;

    protected override string DedupePrefix => "object";

    /// <summary>Lower than the themed default: a cake appears in the handful of
    /// photos around one birthday, not in a dozen.</summary>
    protected override int MinAssets => 8;

    protected override IReadOnlyList<MemoryTheme> Themes => CuratedThemes;

    private static readonly IReadOnlyList<MemoryTheme> CuratedThemes = new List<MemoryTheme>
    {
        Theme("pets", year => $"Tus mascotas en {year}", "cat", "dog"),
        Theme("celebrations", year => $"Celebraciones de {year}", "cake"),
        Theme("food", year => $"Comida de {year}", "pizza", "sandwich", "donut"),
    };

    private static MemoryTheme Theme(string key, Func<int, string> titleFor, params string[] labels) =>
        new(key, titleFor, AssetConditions.HasAnyObjectAbove(labels, MinConfidence));
}
