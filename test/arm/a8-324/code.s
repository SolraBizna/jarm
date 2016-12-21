        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        AND r0, r1, #0xca
        CDP p7, 0, cr0, cr0, cr0, #0
        .endfunc

        .end
