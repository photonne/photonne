#!/bin/sh
# Notas de una release a partir de los Conventional Commits entre la etiqueta
# anterior y el commit que se publica. Lo usa el job `release` de
# docker-image.yml; también se puede lanzar a mano para ver qué saldría:
#
#   ./scripts/release-notes.sh v1.158.1 [HEAD]
#
# Uso: release-notes.sh <nueva-etiqueta> [ref]. Escribe Markdown por stdout.
set -eu

new_tag="$1"
ref="${2:-HEAD}"
max_lines=150
repo="${GITHUB_REPOSITORY:-photonne/photonne}"

# Etiqueta anterior alcanzable desde ref (excluida la nueva, por si ya existe).
prev_tag=$(git describe --tags --abbrev=0 --match 'v*' --exclude "$new_tag" "$ref" 2>/dev/null || true)
range="$ref"
[ -n "$prev_tag" ] && range="$prev_tag..$ref"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# Subject y SHA corto, sin merges. `tipo(ámbito)!: Descripción` se reparte en
# secciones; el ámbito se pinta en negrita.
git log --no-merges --format='%h %s' "$range" | while read -r sha subject; do
    header=${subject%%:*}
    desc=${subject#*: }
    [ "$header" = "$subject" ] && { echo "- $subject ($sha)" >> "$tmp/other"; continue; }
    type=${header%%(*}
    type=${type%!}
    scope=""
    case "$header" in *\(*\)*) scope=${header#*(}; scope=${scope%%)*} ;; esac
    line="- ${scope:+**$scope**: }$desc ($sha)"
    case "$header" in
        *!) echo "$line" >> "$tmp/breaking" ;;
        *)
            case "$type" in
                feat) echo "$line" >> "$tmp/feat" ;;
                fix) echo "$line" >> "$tmp/fix" ;;
                *) echo "$line" >> "$tmp/other" ;;
            esac ;;
    esac
done

total=$(cat "$tmp"/* 2>/dev/null | wc -l | tr -d ' ')
remaining=$max_lines

section() {
    file="$tmp/$1"
    [ -s "$file" ] || return 0
    [ "$remaining" -gt 0 ] || return 0
    printf '## %s\n\n' "$2"
    head -n "$remaining" "$file"
    printf '\n'
    remaining=$((remaining - $(wc -l < "$file" | tr -d ' ')))
}

section breaking "Cambios incompatibles"
section feat "Novedades"
section fix "Correcciones"
section other "Otros cambios"

[ "$total" -eq 0 ] && printf 'Sin cambios desde %s.\n\n' "${prev_tag:-el inicio}"
[ "$total" -gt "$max_lines" ] && printf '_…y %s cambios más._\n\n' "$((total - max_lines))"
if [ -n "$prev_tag" ]; then
    printf '**Cambios completos**: https://github.com/%s/compare/%s...%s\n' "$repo" "$prev_tag" "$new_tag"
fi
