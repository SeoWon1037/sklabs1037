package com.sklabs1037.sklabs1037.persona.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 🧭 투자 성향 유형 규칙 (화면 personaData.js typeOf 와 같아야 함)
 * - 0보다 크면 공격(R)·장기(L)·데이터(D), 0 이하면 안정(S)·단기(Q)·뉴스(N)
 */
class PersonaTypeTest {

    @Test
    void 점수로_유형_정하기() {

        assertEquals("RLD", PersonaController.typeOf(3, 1, 6));
        assertEquals("SQN", PersonaController.typeOf(0, 0, 0));
        assertEquals("SLN", PersonaController.typeOf(-6, 2, -1));
        assertEquals("RQD", PersonaController.typeOf(1, -3, 1));

    }
}
