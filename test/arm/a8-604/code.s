        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        LDR r0, _num
        SETEND BE
        LDR r1, _num
        SETEND LE
        LDR r2, _num
        SETEND BE
        LDR r3, _num
        CDP p7, 0, cr0, cr0, cr0, #0
_num:
        .word 0x12345678
        .endfunc

        .end
