package com.example.audioloop.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.AudioLoopViewModel
import com.example.audioloop.R
import com.example.audioloop.ui.theme.*

@Composable
fun OnboardingScreen(
    onboardingStep: Int,
    viewModel: AudioLoopViewModel,
    themeColors: AppColorPalette
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950)
    ) {
        AnimatedContent(
            targetState = onboardingStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "OnboardingStep"
        ) { step ->
            when (step) {
                1 -> StepWelcome(themeColors) { viewModel.nextOnboardingStep() }
                2 -> StepUseCase(themeColors) { viewModel.selectOnboardingUseCase(it) }
                3 -> StepValueProp(themeColors) { viewModel.nextOnboardingStep() }
                4 -> StepFinal(themeColors) { viewModel.finishOnboarding() }
            }
        }

        // Progress indicators (dots) at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(4) { idx ->
                Box(
                    modifier = Modifier
                        .size(if (onboardingStep == idx + 1) 24.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (onboardingStep == idx + 1) themeColors.primary else Zinc700)
                )
            }
        }
    }
}

@Composable
private fun StepWelcome(themeColors: AppColorPalette, onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Brush.radialGradient(listOf(themeColors.primary.copy(alpha = 0.4f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Loop, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(64.dp))
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                brush = Brush.linearGradient(listOf(Color.White, themeColors.primary200))
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Zinc400,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(60.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.btn_continue), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun StepUseCase(themeColors: AppColorPalette, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(40.dp))
        
        Text(
            text = stringResource(R.string.onboarding_usecase_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        val cases = listOf(
            UseCaseItem("musician", stringResource(R.string.onboarding_usecase_music), AppIcons.GraphicEq),
            UseCaseItem("student", stringResource(R.string.onboarding_usecase_student), AppIcons.School),
            UseCaseItem("podcaster", stringResource(R.string.onboarding_usecase_podcaster), AppIcons.Mic),
            UseCaseItem("general", stringResource(R.string.onboarding_usecase_general), AppIcons.Radio)
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            cases.forEach { item ->
                UseCaseCard(item, themeColors) { onSelect(item.id) }
            }
        }
    }
}

data class UseCaseItem(val id: String, val label: String, val icon: ImageVector)

@Composable
private fun UseCaseCard(item: UseCaseItem, themeColors: AppColorPalette, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Zinc900,
        border = BorderStroke(1.dp, Zinc800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(themeColors.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = null, tint = themeColors.primary)
            }
            Spacer(Modifier.width(16.dp))
            Text(item.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Icon(AppIcons.ChevronRight, contentDescription = null, tint = Zinc600)
        }
    }
}

@Composable
private fun StepValueProp(themeColors: AppColorPalette, onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .border(2.dp, themeColors.primary, CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
             Icon(AppIcons.Loop, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(48.dp))
             // A simulated waveform would be nice here but let's keep it simple
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.onboarding_aha_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = Color.White),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_aha_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Zinc400,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(Modifier.height(60.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.btn_continue), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun StepFinal(themeColors: AppColorPalette, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(AppIcons.Check, contentDescription = null, tint = themeColors.primary, modifier = Modifier.size(80.dp))

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_ready_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_ready_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = Zinc400,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(60.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.btn_got_it), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
