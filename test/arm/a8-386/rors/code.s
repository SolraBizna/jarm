        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        EORS r0, r1, r2, ROR r3
        CDP p7, 0, cr0, cr0, cr0, #0
        .endfunc

        .end
