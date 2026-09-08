using Photonne.Client.Web.Services;
using Xunit;

namespace Photonne.Client.Web.Tests.Services;

public class SearchQueryStateTests
{
    [Fact]
    public void Roundtrip_PreservesAllFields()
    {
        var person = Guid.NewGuid();
        var state = new SearchQueryState
        {
            Query = "playa atardecer",
            Semantic = true,
            From = new DateTime(2024, 1, 15),
            To = new DateTime(2024, 12, 31),
            PersonIds = new[] { person },
            ObjectLabels = new[] { "perro", "coche rojo" },
            SceneLabels = new[] { "playa" },
            Ocr = "menú"
        };

        var parsed = SearchQueryState.Parse(state.ToQueryString());

        Assert.Equal(state.Query, parsed.Query);
        Assert.True(parsed.Semantic);
        Assert.Equal(state.From, parsed.From);
        Assert.Equal(state.To, parsed.To);
        Assert.Equal(new[] { person }, parsed.PersonIds);
        Assert.Equal(state.ObjectLabels, parsed.ObjectLabels);
        Assert.Equal(state.SceneLabels, parsed.SceneLabels);
        Assert.Equal(state.Ocr, parsed.Ocr);
    }

    [Fact]
    public void Roundtrip_IsCanonical()
    {
        var qs = new SearchQueryState { Query = "gato", ObjectLabels = new[] { "árbol" } }.ToQueryString();
        Assert.Equal(qs, SearchQueryState.Parse(qs).ToQueryString());
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("?")]
    public void Parse_EmptyInput_HasNoFilters(string? input)
    {
        Assert.False(SearchQueryState.Parse(input).HasAnyFilter);
    }

    [Fact]
    public void Parse_AcceptsLeadingQuestionMark()
    {
        var parsed = SearchQueryState.Parse("?q=gato&sem=1");
        Assert.Equal("gato", parsed.Query);
        Assert.True(parsed.Semantic);
    }

    [Fact]
    public void Parse_IgnoresInvalidGuidsAndDates()
    {
        var valid = Guid.NewGuid();
        var parsed = SearchQueryState.Parse($"people=no-es-guid,{valid}&from=ayer&to=2024-13-45");

        Assert.Equal(new[] { valid }, parsed.PersonIds);
        Assert.Null(parsed.From);
        Assert.Null(parsed.To);
    }

    [Fact]
    public void ToQueryString_OmitsEmptyFields()
    {
        Assert.Equal("q=gato", new SearchQueryState { Query = "gato" }.ToQueryString());
        Assert.Equal(string.Empty, new SearchQueryState().ToQueryString());
    }

    [Fact]
    public void Roundtrip_EscapesSpecialCharacters()
    {
        var state = new SearchQueryState { Query = "a&b=c?d", Ocr = "50%" };
        var parsed = SearchQueryState.Parse(state.ToQueryString());

        Assert.Equal("a&b=c?d", parsed.Query);
        Assert.Equal("50%", parsed.Ocr);
    }
}
