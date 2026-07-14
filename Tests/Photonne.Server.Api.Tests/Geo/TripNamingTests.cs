using Photonne.Server.Api.Shared.Services.Geo;

namespace Photonne.Server.Api.Tests.Geo;

public class TripNamingTests
{
    private static readonly DateTime July = new(2019, 7, 14, 10, 0, 0);

    [Fact]
    public void OnePlace_IsJustItsName()
    {
        Assert.Equal("Girona", TripNaming.Title([new TripPlaceCount("Girona", 40)], July));
    }

    [Fact]
    public void TwoPlaces_AreOrderedByHowMuchYouShotThere()
    {
        var title = TripNaming.Title(
            [new TripPlaceCount("Florencia", 20), new TripPlaceCount("Roma", 60)],
            July);

        Assert.Equal("Roma y Florencia", title);
    }

    [Fact]
    public void ManyPlaces_NameTheTwoBiggestAndCountTheRest()
    {
        var title = TripNaming.Title(
            [
                new TripPlaceCount("Roma", 60),
                new TripPlaceCount("Florencia", 30),
                new TripPlaceCount("Pisa", 10),
                new TripPlaceCount("Siena", 5),
            ],
            July);

        Assert.Equal("Roma, Florencia y 2 lugares más", title);
    }

    [Fact]
    public void ThreePlaces_UseTheSingularForTheLeftover()
    {
        var title = TripNaming.Title(
            [
                new TripPlaceCount("Roma", 60),
                new TripPlaceCount("Florencia", 30),
                new TripPlaceCount("Pisa", 10),
            ],
            July);

        Assert.Equal("Roma, Florencia y 1 lugar más", title);
    }

    [Fact]
    public void NoPlaces_FallsBackToTheDates()
    {
        // Happens when every coordinate landed far from a populated place. Better
        // an honest month than a trip with no name.
        Assert.Equal("Viaje de julio de 2019", TripNaming.Title([], July));
    }

    [Fact]
    public void Title_IsStableWhenCountsTie()
    {
        // Two runs over the same trip must not alternate between two titles, so a
        // tie breaks on the name, not on dictionary order.
        var places = new[] { new TripPlaceCount("Roma", 20), new TripPlaceCount("Florencia", 20) };

        Assert.Equal(
            TripNaming.Title(places, July),
            TripNaming.Title(places.Reverse().ToList(), July));
    }
}
