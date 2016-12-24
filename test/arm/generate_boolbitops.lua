#!/usr/bin/env lua5.3

math.randomseed(0xDADE)

local function writecode(path, code)
   os.execute("mkdir -p "..path)
   local f = assert(io.open(path.."/code.s","wb"))
   f:write([[
        .include "standard_ivs.s"

        .text
        .code 32

        .global _start
        .func _start
_start:
        ]]..code..[[

        CDP p7, 0, cr0, cr0, cr0, #0
        .endfunc

        .end
]])
   f:close()
end

local condition_registers = {"Z","N","C","V"}
local canon_order_in = {"Z","N","C","V","r0","r1","r2","r3"}
local canon_order_out = {"r0","r1","r2","r3","Z","N","C","V"}

local function writecase(path, name, intab, outtab)
   for _,reg in ipairs(condition_registers) do
      if intab[reg] ~= nil or outtab[reg] ~= nil then
         if intab[reg] == nil then
            intab[reg] = not outtab[reg]
         end
      else
         local r = math.random(0,1) == 0
         intab[reg] = r
         outtab[reg] = r
      end
   end
   local f = assert(io.open(path.."/"..name..".spec","wb"))
   for _,k in ipairs(canon_order_in) do
      local v = intab[k]
      if type(v) == "boolean" then
         f:write(("%s := %i\n"):format(k, v and 1 or 0))
      elseif type(v) == "number" then
         f:write(("%s := 0x%08x\n"):format(k, v))
      end
   end
   f:write("\n")
   for _,k in ipairs(canon_order_out) do
      local v = outtab[k]
      if type(v) == "boolean" then
         f:write(("%s == %i\n"):format(k, v and 1 or 0))
      elseif type(v) == "number" then
         f:write(("%s == 0x%08x\n"):format(k, v))
      end
   end
   f:close()
end

local function generate_immediate(path, op, func)
   writecode(path, op.." r0, r1, #0xca")
   local op2 = 0xca
   for name,op1 in pairs{all_ones=0xffffffff,all_zeroes=0x00000000,
                         ab=0xabababab} do
      writecase(path, name,
                {r1=op1},
                {r0=func(op1,op2)})
   end
   local curpath = path.."/s"
   writecode(curpath, op.."S r0, r1, #0xca")
   for name,op1 in pairs{all_ones=0xffffffff,all_zeroes=0x00000000,
                         ab=0xabababab} do
      local result = func(op1,op2)
      writecase(curpath, name,
                {r1=op1},
                {r0=result,
                 N=(result & 0x80000000) ~= 0,
                 Z=result == 0})
   end
end

local immediate_shift_cases = {
   asr={
      op="ASR #1",
      func=function(x)
         local ret = x >> 1
         if (x & 0x80000000) ~= 0 then ret = ret | 0x80000000 end
         return ret, (x&1) ~= 0
      end
   },
   lsr={
      op="LSR #1",
      func=function(x)
         return x >> 1, (x&1) ~= 0
      end
   },
   lsl={
      op="LSL #1",
      func=function(x)
         return (x << 1) & 0xFFFFFFFF, (x&0x80000000) ~= 0
      end
   },
   ror={
      op="ROR #1",
      func=function(x)
         local ret = x >> 1
         if (x & 1) ~= 0 then ret = ret | 0x80000000 end
         return ret, (x&1) ~= 0
      end
   },
   ror={
      op="RRX",
      func=function(x, cin)
         local ret = x >> 1
         if cin then ret = ret | 0x80000000 end
         return ret, (x&1) ~= 0
      end,
      carry_matters=true,
   },
}

local function generate_register(path, op, func)
   writecode(path, op.." r0, r1, r2")
   writecode(path.."/s", op.."S r0, r1, r2")
   for name,ops in pairs{all_ones={0xffffffff,0xffffffff},
                         all_zeroes={0x00000000,0x00000000},
                         alternating_opposites={0xc3c3c3c3,0x3c3c3c3c},
                         opposites={0x00000000,0xffffffff}} do
      local op1,op2 = ops[1],ops[2]
      local result = func(op1,op2)
      writecase(path, name,
                {r1=op1,r2=op2},
                {r0=result})
      writecase(path.."/s", name,
                {r1=op1,r2=op2},
                {r0=result,
                 N=(result & 0x80000000) ~= 0,
                 Z=result == 0,
                 C=false})
   end
   for casename,case in pairs(immediate_shift_cases) do
      writecode(path.."/"..casename.."s", op.."S r0, r1, r2, "..case.op)
      for name,ops in pairs{all_ones={0xffffffff,0xffffffff},
                            all_zeroes={0x00000000,0x00000000},
                            alternating_opposites={0xc3c3c3c3,0x3c3c3c3c},
                            opposites={0x00000000,0xffffffff},
                            mostly_opposite={0xffffffff,0x80000000},
                            mostly_opposite_2={0xffffffff,0x80000001}} do
         if case.carry_matters then
            local op1,op2 = ops[1],ops[2]
            local rop2,cout = case.func(op2, false)
            local result = func(op1,rop2)
            writecase(path.."/"..casename.."s", name.."_cin0",
                      {r1=op1,r2=op2,C=false},
                      {r0=result,
                       N=(result & 0x80000000) ~= 0,
                       Z=result == 0,
                       C=cout})
            rop2,cout = case.func(op2, true)
            result = func(op1,rop2)
            writecase(path.."/"..casename.."s", name.."_cin1",
                      {r1=op1,r2=op2,C=true},
                      {r0=result,
                       N=(result & 0x80000000) ~= 0,
                       Z=result == 0,
                       C=cout})
         else
            local op1,op2 = ops[1],ops[2]
            local rop2,cout = case.func(op2)
            local result = func(op1,rop2)
            writecase(path.."/"..casename.."s", name,
                      {r1=op1,r2=op2},
                      {r0=result,
                       N=(result & 0x80000000) ~= 0,
                       Z=result == 0,
                       C=cout})
         end
      end
   end
end

local register_shift_cases = {
   asr={
      op="ASR",
      func=function(x)
         local ret = x >> 1
         if (x & 0x80000000) ~= 0 then ret = ret | 0x80000000 end
         return ret, (x&1) ~= 0
      end
   },
   lsr={
      op="LSR",
      func=function(x)
         return x >> 1, (x&1) ~= 0
      end
   },
   lsl={
      op="LSL",
      func=function(x)
         return (x << 1) & 0xFFFFFFFF, (x&0x80000000) ~= 0
      end
   },
   ror={
      op="ROR",
      func=function(x)
         local ret = x >> 1
         if (x & 1) ~= 0 then ret = ret | 0x80000000 end
         return ret, (x&1) ~= 0
      end
   },
}

local function generate_rsr(path, op, func)
   for casename,case in pairs(register_shift_cases) do
      writecode(path.."/"..casename, op.." r0, r1, r2, "..case.op.." r3")
      writecode(path.."/"..casename.."s",op.."S r0, r1, r2, "..case.op.." r3")
      for name,ops in pairs{all_ones={0xffffffff,0xffffffff},
                            all_zeroes={0x00000000,0x00000000},
                            alternating_opposites={0xc3c3c3c3,0x3c3c3c3c},
                            opposites={0x00000000,0xffffffff},
                            mostly_opposite={0xffffffff,0x80000000},
                            mostly_opposite_2={0xffffffff,0x80000001}} do
         local op1,op2 = ops[1],ops[2]
         local rop2,cout = case.func(op2)
         local result = func(op1,rop2)
         writecase(path.."/"..casename, name,
                   {r1=op1,r2=op2,r3=1},
                   {r0=result})
         writecase(path.."/"..casename.."s", name,
                   {r1=op1,r2=op2,r3=1},
                   {r0=result,
                    N=(result & 0x80000000) ~= 0,
                    Z=result == 0,
                    C=cout})
      end
   end
end

local function AND(x,y) return (x&y)&0xFFFFFFFF end
local function EOR(x,y) return (x~y)&0xFFFFFFFF end
local function ORN(x,y) return (x|(~y))&0xFFFFFFFF end
local function ORR(x,y) return (x|y)&0xFFFFFFFF end

generate_immediate("a8-324", "AND", AND)
generate_register("a8-326", "AND", AND)
generate_rsr("a8-328", "AND", AND)

generate_immediate("a8-382", "EOR", EOR)
generate_register("a8-384", "EOR", EOR)
generate_rsr("a8-386", "EOR", EOR)

generate_immediate("a8-512", "ORN", ORN, true)
generate_register("a8-514", "ORN", ORN, true)

generate_immediate("a8-516", "ORR", ORR)
generate_register("a8-518", "ORR", ORR)
generate_rsr("a8-520", "ORR", ORR)

