package com.salsaldrive.app

/** 도착 리포트의 문장을 만드는 곳. 화면 코드와 분리해 두어 테스트하기 쉽게 했습니다. */
object ReportText {

    fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

    fun diff(level: Int, lastLevel: Int?): String {
        if (lastLevel == null) return "첫 주행 기록이에요"
        val d = level - lastLevel
        return when {
            d > 0 -> "지난 주행보다 수위가 ${d}%p 높아요"
            d < 0 -> "지난 주행보다 수위가 ${-d}%p 낮아요"
            else -> "지난 주행과 같은 수위예요"
        }
    }

    /** 뒷자리 손님의 한 줄 후기 */
    fun review(level: Int, events: List<ShakeEvent>): String {
        val brakes = events.count { it.type == ShakeType.BRAKE }
        val others = events.size - brakes
        return when {
            level < 50 -> "별 하나. 다음엔 조금만 살살 부탁드려요."
            level < 65 -> "별 두 개. 컵에 든 물을 절반은 쏟았을 거예요."
            level < 80 -> if (brakes >= others) "별 세 개. 멈출 때마다 몸이 앞으로 쏠렸어요."
                          else "별 세 개. 출발할 때마다 고개가 뒤로 젖혀졌어요."
            level < 90 -> if (events.isNotEmpty()) "별 네 개. ${clock(events.first().secondsFromStart)}쯤 한 번 출렁했지만 대체로 편안했어요."
                          else "별 네 개. 가다 서다가 많아 조금 울렁였어요."
            else -> "별 다섯 개. 타고 있는 줄도 몰랐어요."
        }
    }

    fun tip(events: List<ShakeEvent>): String {
        val b = events.count { it.type == ShakeType.BRAKE }
        val a = events.count { it.type == ShakeType.ACCEL }
        val l = events.count { it.type == ShakeType.LAUNCH }
        return when {
            b > 0 && b >= a && b >= l -> "정지 직전 브레이크를 조금 일찍 나눠 밟아보세요."
            a > 0 && a >= l -> "추월이나 합류 때 가속을 한 박자 길게 가져가 보세요."
            l > 0 -> "출발할 때 가속 페달을 살며시 밟아보세요."
            else -> "다음에도 지금처럼 부드럽게 달려보세요."
        }
    }

    fun coach(type: ShakeType): String = when (type) {
        ShakeType.BRAKE -> "조금만 일찍, 천천히 멈춰볼까요"
        ShakeType.LAUNCH -> "출발은 살며시 해볼까요"
        ShakeType.ACCEL -> "가속을 조금만 부드럽게"
    }
}
