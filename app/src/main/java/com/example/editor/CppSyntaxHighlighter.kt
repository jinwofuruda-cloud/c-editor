package com.example.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

class CppSyntaxHighlighter : VisualTransformation {

    // Theme Colors for Code Highlighting (Dark IDE Theme)
    private val colorComment = Color(0xFF6A9955)      // Green
    private val colorString = Color(0xFFCE9178)       // Orange/Peach
    private val colorDirective = Color(0xFFC586C0)    // Purple
    private val colorKeyword = Color(0xFF569CD6)      // Blue
    private val colorStdLib = Color(0xFF4EC9B0)       // Teal
    private val colorNumber = Color(0xFFB5CEA8)       // Light Green
    private val colorDefault = Color(0xFFD4D4D4)      // Off-white

    private val masterRegex = Regex(
        "(//.*)|" +                               // Group 1: Single line comment
        "(/\\*[\\s\\S]*?\\*/)|" +                 // Group 2: Multi-line comment
        "(\"(?:\\\\.|[^\"\\\\])*\")|" +            // Group 3: Double quoted string
        "('(?:\\\\.|[^'\\\\])*')|" +              // Group 4: Single quoted char
        "(#[a-zA-Z_]+)|" +                        // Group 5: Preprocessor directives (e.g. #include)
        "(\\b(?:alignas|alignof|and|and_eq|asm|atomic_cancel|atomic_commit|atomic_noexcept|auto|bitand|bitor|bool|break|case|catch|char|char8_t|char16_t|char32_t|class|compl|concept|const|consteval|constexpr|constinit|const_cast|continue|co_await|co_return|co_yield|decltype|default|delete|do|double|dynamic_cast|else|enum|explicit|export|extern|false|float|for|friend|goto|if|inline|int|long|mutable|namespace|new|noexcept|not|not_eq|nullptr|operator|or|or_eq|private|protected|public|reflexpr|register|reinterpret_cast|requires|return|short|signed|sizeof|static|static_assert|static_cast|struct|switch|template|this|thread_local|throw|true|try|typedef|typeid|typename|union|unsigned|using|virtual|void|volatile|wchar_t|while|xor|xor_eq)\\b)|" + // Group 6: C++ Keywords
        "(\\b(?:std|cout|cin|endl|string|vector|map|set|list|unique_ptr|shared_ptr|make_unique|make_shared|printf|scanf|size_t|pair|cerr|clog)\\b)|" + // Group 7: Standard Library types/objects
        "(\\b\\d+(?:\\.\\d+)?\\b|\\b0x[a-fA-F0-9]+\\b)" // Group 8: Numeric values
    )

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlight(text.text), OffsetMapping.Identity)
    }

    private fun highlight(rawText: String): AnnotatedString {
        val builder = AnnotatedString.Builder(rawText)
        
        // Start by applying default color to the entire range
        builder.addStyle(SpanStyle(color = colorDefault), 0, rawText.length)

        val matches = masterRegex.findAll(rawText)
        for (match in matches) {
            val range = match.range
            val groups = match.groups
            
            when {
                // Group 1 & 2: Comments
                groups[1] != null || groups[2] != null -> {
                    builder.addStyle(SpanStyle(color = colorComment), range.first, range.last + 1)
                }
                // Group 3 & 4: Strings / Characters
                groups[3] != null || groups[4] != null -> {
                    builder.addStyle(SpanStyle(color = colorString), range.first, range.last + 1)
                }
                // Group 5: Preprocessor
                groups[5] != null -> {
                    builder.addStyle(SpanStyle(color = colorDirective, fontWeight = FontWeight.Bold), range.first, range.last + 1)
                }
                // Group 6: Keywords
                groups[6] != null -> {
                    builder.addStyle(SpanStyle(color = colorKeyword, fontWeight = FontWeight.Bold), range.first, range.last + 1)
                }
                // Group 7: Std library identifiers
                groups[7] != null -> {
                    builder.addStyle(SpanStyle(color = colorStdLib), range.first, range.last + 1)
                }
                // Group 8: Numbers
                groups[8] != null -> {
                    builder.addStyle(SpanStyle(color = colorNumber), range.first, range.last + 1)
                }
            }
        }

        return builder.toAnnotatedString()
    }
}
