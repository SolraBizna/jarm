        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        CMN r1, #32
        CDP p7, 0, cr0, cr0, cr0, #0
        .endfunc

        .end
