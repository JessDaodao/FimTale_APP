#pragma once

#include <cstdint>
#include <string_view>
#include <vector>

namespace fimtale::bbcode {

// All offsets are UTF-16 code units into the unmodified Java source string.
// One record is: start, contentStart, contentEnd, end, nameStart, nameEnd,
// argumentStart, argumentEnd, attributeCount, then four offsets per attribute
// (keyStart, keyEnd, valueStart, valueEnd). Java owns the resulting strings.
std::vector<std::int32_t> parseSyntax(std::u16string_view source);

} // namespace fimtale::bbcode
