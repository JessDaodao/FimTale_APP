#include "bbcode-syntax.h"

#include <algorithm>
#include <limits>
#include <stdexcept>
#include <utility>

namespace fimtale::bbcode {
namespace {

struct Range {
    int start = 0;
    int end = 0;
};

struct Attribute {
    Range key;
    Range value;
};

struct Node {
    int start, contentStart, contentEnd = -1, end = -1;
    Range name, argument;
    std::vector<Attribute> attributes;
};

char16_t lower(char16_t c) {
    return c >= u'A' && c <= u'Z' ? c + (u'a' - u'A') : c;
}

bool letter(char16_t c) {
    c = lower(c);
    return c >= u'a' && c <= u'z';
}

bool nameCharacter(char16_t c) {
    return letter(c) || (c >= u'0' && c <= u'9') || c == u'-';
}

bool keyCharacter(char16_t c) {
    return nameCharacter(c) || c == u'_';
}

// Java Pattern's default \s is ASCII; String.trim() has a different rule.
bool space(char16_t c) {
    return c == u' ' || (c >= u'\t' && c <= u'\r');
}

Range trim(std::u16string_view source, Range range) {
    while (range.start < range.end && source[range.start] <= 0x20) ++range.start;
    while (range.end > range.start && source[range.end - 1] <= 0x20) --range.end;
    return range;
}

bool same(std::u16string_view a, std::u16string_view b) {
    return a.size() == b.size() && std::equal(a.begin(), a.end(), b.begin(),
            [](char16_t x, char16_t y) { return lower(x) == lower(y); });
}

std::u16string_view name(std::u16string_view source, const Node& node) {
    return source.substr(node.name.start, node.name.end - node.name.start);
}

// Precompute where a regex-compatible tag tail ends, including quoted ']'.
// This keeps repeated malformed openings/quotes linear instead of rescanning
// the remainder of a long document for every '['.
std::vector<int> tailEnds(std::u16string_view source) {
    std::vector<int> ends(source.size() + 1, -1);
    int single = -1, doubleQuote = -1;
    for (int i = static_cast<int>(source.size()) - 1; i >= 0; --i) {
        char16_t c = source[i];
        if (c == u']') ends[i] = i + 1;
        else if (c == u'\'' || c == u'"') {
            int& next = c == u'\'' ? single : doubleQuote;
            if (next >= 0) ends[i] = ends[next + 1];
            next = i;
        } else ends[i] = ends[i + 1];
    }
    return ends;
}

bool nextToken(std::u16string_view source, const std::vector<int>& ends,
               int& cursor, Node& node, bool& closing) {
    const int length = static_cast<int>(source.size());
    while (cursor < length) {
        int start = cursor++;
        if (source[start] != u'[') continue;
        int p = cursor;
        closing = p < length && source[p] == u'/';
        if (closing) ++p;
        int nameStart = p;
        if (p >= length) continue;
        if (source[p] == u'*') ++p;
        else {
            if (!letter(source[p])) continue;
            while (p < length && nameCharacter(source[p])) ++p;
        }
        if (ends[p] < 0) continue;
        node.start = start;
        node.contentStart = ends[p];
        node.name = {nameStart, p};
        cursor = ends[p];
        return true;
    }
    return false;
}

void attributes(std::u16string_view source, Node& node) {
    Range tail = trim(source, {node.name.end, node.contentStart - 1});
    if (tail.start == tail.end) return;
    if (source[tail.start] == u'=') {
        node.argument = trim(source, {tail.start + 1, tail.end});
        auto& arg = node.argument;
        if (arg.end - arg.start >= 2 && (source[arg.start] == u'\'' || source[arg.start] == u'"')
                && source[arg.start] == source[arg.end - 1]) {
            ++arg.start;
            --arg.end;
        }
        return;
    }
    int p = tail.start;
    while (p < tail.end) {
        if (!keyCharacter(source[p])) { ++p; continue; }
        Range key{p, p};
        while (p < tail.end && keyCharacter(source[p])) ++p;
        key.end = p;
        while (p < tail.end && space(source[p])) ++p;
        if (p == tail.end || source[p] != u'=') continue;
        ++p;
        while (p < tail.end && space(source[p])) ++p;
        if (p == tail.end) break;
        Range value{p, p};
        if (source[p] == u'\'' || source[p] == u'"') {
            const auto end = source.find(source[p], p + 1);
            if (end != std::u16string_view::npos && end < static_cast<std::size_t>(tail.end)) {
                value = {p + 1, static_cast<int>(end)};
                p = static_cast<int>(end) + 1;
                node.attributes.push_back({key, value});
                continue;
            }
        }
        // The old regex falls back to an unquoted value if a quote is unmatched.
        while (p < tail.end && !space(source[p])) ++p;
        value.end = p;
        node.attributes.push_back({key, value});
    }
}

int rawEnd(std::u16string_view source, std::u16string_view tag, int cursor) {
    while (cursor < static_cast<int>(source.size())) {
        auto start = source.find(u'[', cursor);
        if (start == std::u16string_view::npos) return -1;
        if (source.size() - start >= tag.size() + 3 && source[start + 1] == u'/'
                && source[start + tag.size() + 2] == u']'
                && same(source.substr(start + 2, tag.size()), tag)) return static_cast<int>(start);
        cursor = static_cast<int>(start) + 1;
    }
    return -1;
}

void close(Node node, int contentEnd, int end, std::vector<Node>& result) {
    node.contentEnd = contentEnd;
    node.end = end;
    result.push_back(std::move(node));
}

} // namespace

std::vector<std::int32_t> parseSyntax(std::u16string_view source) {
    if (source.size() > static_cast<std::size_t>(std::numeric_limits<int>::max()))
        throw std::length_error("BBCode source is too large");
    if (source.empty()) return {};
    auto ends = tailEnds(source);
    std::vector<Node> stack, result;
    int cursor = 0;
    while (cursor < static_cast<int>(source.size())) {
        Node node{};
        bool closing = false;
        if (!nextToken(source, ends, cursor, node, closing)) break;
        auto tag = name(source, node);
        if (!closing) {
            if (tag == u"*") {
                int list = static_cast<int>(stack.size()) - 1;
                while (list >= 0 && !same(name(source, stack[list]), u"list")) --list;
                if (list < 0) continue;
                while (static_cast<int>(stack.size()) > list + 1) {
                    if (name(source, stack.back()) == u"*")
                        close(std::move(stack.back()), node.start, node.start, result);
                    stack.pop_back();
                }
            }
            if (stack.size() >= 128) continue;
            attributes(source, node);
            if (same(tag, u"br") || same(tag, u"hr")) {
                close(std::move(node), cursor, cursor, result);
            } else if (same(tag, u"code") || same(tag, u"markdown") || same(tag, u"img") || same(tag, u"handbook")) {
                int end = rawEnd(source, tag, cursor);
                if (end < 0) break;
                cursor = end + static_cast<int>(tag.size()) + 3;
                close(std::move(node), end, cursor, result);
            } else stack.push_back(std::move(node));
        } else {
            for (int i = static_cast<int>(stack.size()) - 1; i >= 0; --i) {
                if (!same(name(source, stack[i]), tag)) continue;
                for (int inner = static_cast<int>(stack.size()) - 1; inner > i; --inner) {
                    if (name(source, stack[inner]) == u"*")
                        close(std::move(stack[inner]), node.start, node.start, result);
                }
                close(std::move(stack[i]), node.start, cursor, result);
                stack.resize(i);
                break;
            }
        }
    }
    std::sort(result.begin(), result.end(), [](const Node& a, const Node& b) { return a.start < b.start; });
    std::vector<std::int32_t> packed;
    for (const auto& node : result) {
        packed.insert(packed.end(), {node.start, node.contentStart, node.contentEnd, node.end,
                node.name.start, node.name.end, node.argument.start, node.argument.end,
                static_cast<std::int32_t>(node.attributes.size())});
        for (const auto& attr : node.attributes)
            packed.insert(packed.end(), {attr.key.start, attr.key.end, attr.value.start, attr.value.end});
    }
    return packed;
}

} // namespace fimtale::bbcode
