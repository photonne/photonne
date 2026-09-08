namespace Photonne.Client.Web.Services;

/// <summary>
/// Movimiento del foco por teclado en una rejilla justificada, expresada como
/// filas de ids en orden visual. ←/→ recorren el orden plano (saltando de
/// fila); ↑/↓ van a la fila adyacente conservando la posición dentro de la
/// fila (clampada) — la aproximación estándar de las galerías.
/// </summary>
public static class GridKeyboardNav
{
    public static Guid? Move(IReadOnlyList<IReadOnlyList<Guid>> rows, Guid? current, string direction)
    {
        if (rows.Count == 0) return null;
        if (current == null || !Locate(rows, current.Value, out var rowIndex, out var colIndex))
        {
            return rows[0].Count > 0 ? rows[0][0] : null;
        }

        switch (direction)
        {
            case "left":
                if (colIndex > 0) return rows[rowIndex][colIndex - 1];
                return rowIndex > 0 ? rows[rowIndex - 1][^1] : current;
            case "right":
                if (colIndex < rows[rowIndex].Count - 1) return rows[rowIndex][colIndex + 1];
                return rowIndex < rows.Count - 1 ? rows[rowIndex + 1][0] : current;
            case "up":
                return rowIndex > 0
                    ? rows[rowIndex - 1][Math.Min(colIndex, rows[rowIndex - 1].Count - 1)]
                    : current;
            case "down":
                return rowIndex < rows.Count - 1
                    ? rows[rowIndex + 1][Math.Min(colIndex, rows[rowIndex + 1].Count - 1)]
                    : current;
            default:
                return current;
        }
    }

    private static bool Locate(IReadOnlyList<IReadOnlyList<Guid>> rows, Guid id,
        out int rowIndex, out int colIndex)
    {
        for (var r = 0; r < rows.Count; r++)
        {
            var c = rows[r].ToList().IndexOf(id);
            if (c >= 0)
            {
                rowIndex = r;
                colIndex = c;
                return true;
            }
        }
        rowIndex = colIndex = -1;
        return false;
    }
}
