using Photonne.Server.Api.Shared.Models;
using Photonne.Server.Api.Shared.Services.SmartAlbums;

namespace Photonne.Server.Api.Features.Memories.Generation;

/// <summary>
/// Memories from what the scene classifier saw: beaches, snow, forests.
///
/// Twelve themes out of Places365's 365 classes, chosen by hand. The other 353
/// are either indistinguishable from each other in a family photo library
/// ("residential neighborhood" vs "driveway"), or nobody wants a keepsake of
/// them ("parking lot", "waiting room"). Raw Places365 labels are also unusable
/// as titles — "apartment building outdoor" — so every theme carries its own
/// Spanish wording.
///
/// Labels are the exact flattened strings the ML service emits (see
/// Src/Photonne.MlService/app/places365_labels.py). A typo here is invisible: it
/// silently matches nothing and the theme just never appears.
/// </summary>
internal sealed class CuratedSceneGenerator : ThemedMemoryGenerator
{
    /// <summary>
    /// Well above the pipeline's 0.15 storage floor. Places365 is a softmax over
    /// 365 classes, so 0.45 effectively means the model ranked it first and wasn't
    /// hedging — the bar a photo must clear to be called "a beach day".
    /// </summary>
    private const float MinConfidence = 0.45f;

    public override MemoryKind Kind => MemoryKind.CuratedScene;

    protected override string DedupePrefix => "scene";

    protected override IReadOnlyList<MemoryTheme> Themes => CuratedThemes;

    private static readonly IReadOnlyList<MemoryTheme> CuratedThemes = new List<MemoryTheme>
    {
        Theme("beach", "Días de playa", year => $"Días de playa de {year}", "beach", "coast", "ocean"),
        Theme("snow", "Días de nieve", year => $"Días de nieve de {year}", "mountain snowy", "snowfield", "ski slope", "ski resort"),
        Theme("mountain", "Montaña", year => $"Montaña en {year}", "mountain", "mountain path", "cliff", "valley"),
        Theme("forest", "Bosques", year => $"Bosques de {year}", "forest broadleaf", "forest path", "forest road", "rainforest", "bamboo forest"),
        Theme("water", "Lagos y ríos", year => $"Lagos y ríos de {year}", "lake natural", "river", "waterfall", "creek"),
        Theme("pool", "Piscina", year => $"Piscina en {year}", "swimming pool outdoor", "swimming pool indoor", "water park"),
        Theme("dining", "Comidas fuera", year => $"Comidas fuera en {year}", "restaurant", "restaurant patio", "bar", "beer garden", "coffee shop"),
        Theme("monuments", "Museos y monumentos", year => $"Museos y monumentos de {year}", "museum indoor", "museum outdoor", "castle", "palace", "ruin", "church outdoor"),
        Theme("garden", "Jardines", year => $"Jardines de {year}", "botanical garden", "formal garden", "japanese garden", "topiary garden"),
        Theme("stadium", "Estadios", year => $"Estadios en {year}", "stadium baseball", "stadium football", "stadium soccer", "arena performance"),
        Theme("camping", "Camping y picnic", year => $"Camping y picnic en {year}", "campsite", "picnic area"),
        Theme("city", "Por la ciudad", year => $"Por la ciudad en {year}", "downtown", "plaza", "street", "alley"),
    };

    private static MemoryTheme Theme(
        string key,
        string groupTitle,
        Func<int, string> titleFor,
        params string[] labels) =>
        new(key, groupTitle, titleFor, AssetConditions.HasAnySceneAbove(labels, MinConfidence));
}
