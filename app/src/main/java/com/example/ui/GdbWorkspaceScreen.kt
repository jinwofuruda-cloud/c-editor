package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.CppSyntaxHighlighter
import com.example.model.TerminalLine
import com.example.model.TerminalLineType
import com.example.model.VariableInfo
import com.example.viewmodel.ExecutionMode
import com.example.viewmodel.GdbWorkspaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GdbWorkspaceScreen(
    viewModel: GdbWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val editorState by viewModel.editorTextState.collectAsState()
    val breakpoints by viewModel.breakpoints.collectAsState()
    val executionMode by viewModel.executionMode.collectAsState()
    val terminalLines by viewModel.terminalLines.collectAsState()
    val terminalInput by viewModel.terminalInputBuffer.collectAsState()
    val activeHighlightLine by viewModel.activeHighlightLine.collectAsState()
    val activeVariables by viewModel.activeVariables.collectAsState()

    // Editor vertical scroll state, synced with line numbers column
    val editorScrollState = rememberScrollState()
    val terminalListState = rememberLazyListState()

    // Automatically scroll terminal to the bottom when new output arrives
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLines.lastIndex)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // Dark professional IDE background
    ) {
        val isTablet = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color(0xFF4EC9B0),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "OnlineGDB C++ IDE",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF121212),
                        titleContentColor = Color.White
                    ),
                    actions = {
                        // Quick status badge
                        val statusText = when (executionMode) {
                            is ExecutionMode.Idle -> "IDLE"
                            is ExecutionMode.Compiling -> "COMPILING"
                            is ExecutionMode.Running -> "RUNNING"
                            is ExecutionMode.Debugging -> "DEBUGGING"
                            is ExecutionMode.PausedForInput -> "WAITING"
                            is ExecutionMode.Error -> "ERROR"
                        }
                        val badgeColor = when (executionMode) {
                            is ExecutionMode.Idle -> Color(0xFF8B949E)
                            is ExecutionMode.Compiling -> Color(0xFFE3B341)
                            is ExecutionMode.Running -> Color(0xFF2EA043)
                            is ExecutionMode.Debugging -> Color(0xFFFF7B72)
                            is ExecutionMode.PausedForInput -> Color(0xFF569CD6)
                            is ExecutionMode.Error -> Color(0xFFF85149)
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, badgeColor),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = TextStyle(
                                    color = badgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                )
            },
            containerColor = Color(0xFF1E1E1E)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isTablet) {
                    // Landscape Split View: Left (Editor + Controls), Right (Terminal + Variables)
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .border(1.dp, Color(0xFF2D2D2D))
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                CodeEditorPane(
                                    editorState = editorState,
                                    breakpoints = breakpoints,
                                    activeHighlightLine = activeHighlightLine,
                                    scrollState = editorScrollState,
                                    onValueChange = { viewModel.updateEditorState(it) },
                                    onBreakpointToggle = { viewModel.toggleBreakpoint(it) }
                                )
                            }
                            ControlBar(
                                executionMode = executionMode,
                                onRun = { viewModel.runCode() },
                                onDebug = { viewModel.debugCode() },
                                onStop = { viewModel.stopExecution() },
                                onStepNext = { viewModel.stepDebugNext() },
                                onContinue = { viewModel.continueDebug() }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.9f)
                                .fillMaxHeight()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                TerminalConsolePane(
                                    terminalLines = terminalLines,
                                    terminalInput = terminalInput,
                                    executionMode = executionMode,
                                    listState = terminalListState,
                                    onInputChange = { viewModel.updateTerminalInput(it) },
                                    onInputSubmit = { viewModel.submitTerminalInput() }
                                )
                            }
                            VariableWatcherPane(
                                variables = activeVariables,
                                isVisible = executionMode is ExecutionMode.Debugging,
                                modifier = Modifier.height(220.dp)
                            )
                        }
                    }
                } else {
                    // Portrait Stacked View: Top (Editor), Middle (Control Bar), Bottom (Terminal), Drawer/Popup (Variables)
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxWidth()
                        ) {
                            CodeEditorPane(
                                editorState = editorState,
                                breakpoints = breakpoints,
                                activeHighlightLine = activeHighlightLine,
                                scrollState = editorScrollState,
                                onValueChange = { viewModel.updateEditorState(it) },
                                onBreakpointToggle = { viewModel.toggleBreakpoint(it) }
                            )
                        }

                        ControlBar(
                            executionMode = executionMode,
                            onRun = { viewModel.runCode() },
                            onDebug = { viewModel.debugCode() },
                            onStop = { viewModel.stopExecution() },
                            onStepNext = { viewModel.stepDebugNext() },
                            onContinue = { viewModel.continueDebug() }
                        )

                        Box(
                            modifier = Modifier
                                .weight(0.8f)
                                .fillMaxWidth()
                        ) {
                            TerminalConsolePane(
                                terminalLines = terminalLines,
                                terminalInput = terminalInput,
                                executionMode = executionMode,
                                listState = terminalListState,
                                onInputChange = { viewModel.updateTerminalInput(it) },
                                onInputSubmit = { viewModel.submitTerminalInput() }
                            )
                        }

                        // Sliding drawer/bottom sheet for Variable Watcher on mobile
                        AnimatedVisibility(
                            visible = executionMode is ExecutionMode.Debugging && activeVariables.isNotEmpty(),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            VariableWatcherPane(
                                variables = activeVariables,
                                isVisible = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeEditorPane(
    editorState: TextFieldValue,
    breakpoints: Set<Int>,
    activeHighlightLine: Int?,
    scrollState: ScrollState,
    onValueChange: (TextFieldValue) -> Unit,
    onBreakpointToggle: (Int) -> Unit
) {
    val density = LocalDensity.current
    val lineHeight = 22.sp
    val lineHeightPx = with(density) { lineHeight.toPx() }

    // Colors
    val editorBg = Color(0xFF1E1E1E)
    val gutterBg = Color(0xFF181818)
    val highlightColor = Color(0x3FDDCE27) // soft yellow for executing line
    val breakpointGutterColor = Color(0x3FF85149)

    val lineCount = maxOf(editorState.text.split("\n").size, 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(editorBg)
    ) {
        // Code pane header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = Color(0xFF569CD6),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "main.cpp",
                style = TextStyle(
                    color = Color(0xFFD4D4D4),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "UTF-8",
                style = TextStyle(color = Color(0xFF7F7F7F), fontSize = 11.sp)
            )
        }

        // Main editor scroll container
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .horizontalScroll(rememberScrollState())
        ) {
            // Left Gutter: Breakpoint triggers and Line numbers
            Column(
                modifier = Modifier
                    .background(gutterBg)
                    .width(48.dp)
                    .drawBehind {
                        // Draw subtle vertical line separating gutter and editor
                        drawLine(
                            color = Color(0xFF2F2F2F),
                            start = Offset(size.width, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1f
                        )
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (i in 1..lineCount) {
                    val isBreakpoint = breakpoints.contains(i)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { lineHeight.toDp() })
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onBreakpointToggle(i)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Breakpoint red indicator
                        if (isBreakpoint) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF85149))
                            )
                        } else {
                            Text(
                                text = i.toString(),
                                style = TextStyle(
                                    color = Color(0xFF858585),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }

            // Right Pane: Actual editable code area with highlighting and highlight line
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Draw soft yellow background for the active breakpoint line
                        if (activeHighlightLine != null && activeHighlightLine in 1..lineCount) {
                            val topOffset = (activeHighlightLine - 1) * lineHeightPx
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(0f, topOffset),
                                size = Size(size.width, lineHeightPx)
                            )
                        }
                        // Draw soft pink background highlight for all breakpoint lines
                        for (bp in breakpoints) {
                            if (bp in 1..lineCount && bp != activeHighlightLine) {
                                val topOffset = (bp - 1) * lineHeightPx
                                drawRect(
                                    color = breakpointGutterColor,
                                    topLeft = Offset(0f, topOffset),
                                    size = Size(size.width, lineHeightPx)
                                )
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                BasicTextField(
                    value = editorState,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cpp_code_editor"),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = lineHeight,
                        color = Color(0xFFD4D4D4)
                    ),
                    cursorBrush = SolidColor(Color.White),
                    visualTransformation = CppSyntaxHighlighter()
                )
            }
        }
    }
}

@Composable
fun ControlBar(
    executionMode: ExecutionMode,
    onRun: () -> Unit,
    onDebug: () -> Unit,
    onStop: () -> Unit,
    onStepNext: () -> Unit,
    onContinue: () -> Unit
) {
    val isRunningOrCompiling = executionMode is ExecutionMode.Running || 
            executionMode is ExecutionMode.Compiling || 
            executionMode is ExecutionMode.Debugging || 
            executionMode is ExecutionMode.PausedForInput

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF151515))
            .border(BorderStroke(1.dp, Color(0xFF282828)))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Run Button
        Button(
            onClick = onRun,
            enabled = !isRunningOrCompiling,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2EA043),
                disabledContainerColor = Color(0xFF1E3A22)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.testTag("run_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Run", fontSize = 13.sp)
        }

        // Debug Button
        Button(
            onClick = onDebug,
            enabled = !isRunningOrCompiling,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF7B72),
                disabledContainerColor = Color(0xFF4C2725)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.testTag("debug_button")
        ) {
            Icon(Icons.Default.BugReport, contentDescription = "Debug", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Debug", fontSize = 13.sp)
        }

        // Stop Button
        Button(
            onClick = onStop,
            enabled = isRunningOrCompiling,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF85149),
                disabledContainerColor = Color(0xFF4A1512)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.testTag("stop_button")
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Stop", fontSize = 13.sp)
        }

        // Debug Step navigation items
        if (executionMode is ExecutionMode.Debugging || executionMode is ExecutionMode.PausedForInput) {
            VerticalDivider(modifier = Modifier.height(24.dp), color = Color(0xFF444444))

            // Step Over/Next
            FilledIconButton(
                onClick = onStepNext,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF569CD6)
                ),
                modifier = Modifier
                    .size(36.dp)
                    .testTag("step_next_button")
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Step Next", modifier = Modifier.size(18.dp))
            }

            // Continue Debugging
            FilledIconButton(
                onClick = onContinue,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFE3B341)
                ),
                modifier = Modifier
                    .size(36.dp)
                    .testTag("continue_debug_button")
            ) {
                Icon(Icons.Default.FastForward, contentDescription = "Continue Run", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun TerminalConsolePane(
    terminalLines: List<TerminalLine>,
    terminalInput: String,
    executionMode: ExecutionMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onInputChange: (String) -> Unit,
    onInputSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)) // Deep absolute terminal black
    ) {
        // Terminal header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181818))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = Color(0xFFE3B341),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Terminal Console (STDIN / STDOUT)",
                style = TextStyle(
                    color = Color(0xFFCCCCCC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
        }

        // Terminal lines scroll list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(terminalLines) { line ->
                val color = when (line.type) {
                    TerminalLineType.SYSTEM -> Color(0xFF8B949E)  // Gray system message
                    TerminalLineType.STDOUT -> Color(0xFFD4D4D4)  // Clear light stdout
                    TerminalLineType.STDIN -> Color(0xFF56D364)   // Green user inputs
                    TerminalLineType.ERROR -> Color(0xFFF85149)   // Red error outputs
                }
                val prefix = when (line.type) {
                    TerminalLineType.SYSTEM -> "[GDB] "
                    TerminalLineType.STDIN -> "> "
                    else -> ""
                }
                Text(
                    text = "$prefix${line.text}",
                    style = TextStyle(
                        color = color,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // STDIN Input Field (at bottom of terminal)
        val isPausedForInput = executionMode is ExecutionMode.PausedForInput

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPausedForInput) Color(0xFF1A1F1C) else Color(0xFF121212))
                .border(
                    BorderStroke(
                        1.dp,
                        if (isPausedForInput) Color(0xFF2EA043) else Color(0xFF222222)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "stdin> ",
                style = TextStyle(
                    color = if (isPausedForInput) Color(0xFF56D364) else Color(0xFF8B949E),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            BasicTextField(
                value = terminalInput,
                onValueChange = onInputChange,
                enabled = isPausedForInput,
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_stdin_input"),
                textStyle = TextStyle(
                    color = if (isPausedForInput) Color.White else Color(0xFF555555),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(Color(0xFF56D364)),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (terminalInput.isNotBlank()) onInputSubmit()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (terminalInput.isEmpty()) {
                            Text(
                                text = if (isPausedForInput) "Provide input..." else "Process execution idle",
                                style = TextStyle(
                                    color = Color(0xFF444444),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (isPausedForInput) {
                IconButton(
                    onClick = { if (terminalInput.isNotBlank()) onInputSubmit() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send stdin",
                        tint = Color(0xFF2EA043),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VariableWatcherPane(
    variables: List<VariableInfo>,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414))
                .border(BorderStroke(1.dp, Color(0xFF282828)))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C1C))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Troubleshoot,
                    contentDescription = null,
                    tint = Color(0xFFFF7B72),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "GDB Variable Watcher",
                    style = TextStyle(
                        color = Color(0xFFE5E5E5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            if (variables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No local variables currently in scope.",
                        style = TextStyle(
                            color = Color(0xFF7F7F7F),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(variables) { variable ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(BorderStroke(1.dp, Color(0xFF222222)), RoundedCornerShape(4.dp))
                                .background(Color(0xFF1A1A1A))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Var Type
                            Text(
                                text = variable.type,
                                style = TextStyle(
                                    color = Color(0xFF569CD6),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Var Name
                            Text(
                                text = variable.name,
                                style = TextStyle(
                                    color = Color(0xFF9CDCFE),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // Equal Sign
                            Text(
                                text = "=",
                                style = TextStyle(
                                    color = Color(0xFFD4D4D4),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            // Var Value
                            Text(
                                text = variable.value,
                                style = TextStyle(
                                    color = Color(0xFFCE9178),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
