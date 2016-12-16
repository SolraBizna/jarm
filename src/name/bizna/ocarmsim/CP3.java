/* taken unmodified from OC-ARM, except for this next line: */
package name.bizna.ocarmsim;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.ExecutionResult;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.machine.Machine;
import li.cil.oc.api.machine.Signal;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Node;
import name.bizna.jarm.AlignmentException;
import name.bizna.jarm.BusErrorException;
import name.bizna.jarm.CPU;
import name.bizna.jarm.EscapeCompleteException;
import name.bizna.jarm.EscapeRetryException;
import name.bizna.jarm.SaneCoprocessor;
import name.bizna.jarm.UndefinedException;
import name.bizna.jarm.VirtualMemorySpace;

public class CP3 extends SaneCoprocessor {
	
	protected transient VirtualMemorySpace vm;
	protected transient Machine machine;
	protected transient JARMArchitecture parent;
	
	/*** Registers ***/
	/* Signal Buffer */
	protected String signalBufferName;
	protected Object[] signalBufferArgs;
	/* Invoke Buffer */
	protected String invokeBufferFunc;
	protected Object[] invokeBufferParams;
	/* Reply Buffer */
	protected Integer replyResult;
	protected Object[] replyBuffer;
	/* Async Invoke Buffer */
	protected String asyncInvokeBufferFunc;
	protected Object[] asyncInvokeBufferParams;
	/* Async Reply Buffer */
	protected Integer asyncReplyResult;
	protected Object[] asyncReplyBuffer;
	/* Interchange Control Register */
	protected boolean icr_INTERCHANGE_PACKED;
	/* Invoke Target Register */
	protected int invokeTargetRegister;
	/* Memory Module Index Register */
	protected int memoryModuleIndexRegister;
	/* Component List Buffer */
	protected Object[] componentListBuffer;
	/* Compact Component Index Register */
	protected int compactComponentIndexRegister;
	/* Interchange Store Truncation Register */
	protected int interchangeStoreTruncationRegister;
	
	/*** Synchronized State ***/
	protected boolean syncInvokeDiscardReply;
	protected String syncInvokeOnComponent;
	protected String syncInvokeFunc;
	protected Object[] syncInvokeParams;
	protected String asyncInvokeOnComponent;
	protected String asyncInvokeFunc;
	protected Object[] asyncInvokeParams;
	protected ExecutionResult overrideResult;

	private static class EndSentinel {}
	private static final EndSentinel END_SENTINEL = new EndSentinel();

	public void reset() {
		signalBufferName = null;
		signalBufferArgs = null;
		invokeBufferFunc = null;
		invokeBufferParams = null;
		replyResult = Integer.valueOf(OCARM.INVOKE_SUCCESS);
		replyBuffer = new Object[]{};
		asyncInvokeBufferFunc = null;
		asyncInvokeBufferParams = null;
		asyncReplyResult = Integer.valueOf(OCARM.INVOKE_SUCCESS);
		asyncReplyBuffer = new Object[]{};
		syncInvokeOnComponent = null;
		syncInvokeFunc = null;
		syncInvokeParams = null;
		asyncInvokeOnComponent = null;
		asyncInvokeFunc = null;
		asyncInvokeParams = null;
		overrideResult = null;
		icr_INTERCHANGE_PACKED = false;
		componentListBuffer = null;
		invokeTargetRegister = 0;
		memoryModuleIndexRegister = 0;
		compactComponentIndexRegister = 0;
		interchangeStoreTruncationRegister = 0;
	}
	
	public boolean mayExecute() {
		return syncInvokeOnComponent == null;
	}
	public ExecutionResult getExecutionResult() {
		if(syncInvokeOnComponent != null) return new ExecutionResult.SynchronizedCall();
		else if(overrideResult != null) {
			ExecutionResult ret = overrideResult;
			overrideResult = null;
			return ret;
		}
		else return null;
	}
	private boolean isFakeMethod(String method) {
		if(method.equals("_getMethods")) return true;
		else return false;
	}
	private Object[] hardwareInvoke(String address, String method, Object[] params) throws Exception {
		if(method.equals("_getMethods")) {
			Node node = machine.node().network().node(address);
			if(node == null || !(node instanceof Component)) return new Object[]{null, "node disappeared?"};
			Component component = (Component)node;
			TreeMap<Object, Object> ret = new TreeMap<Object, Object>();
			for(String name : component.methods()) {
				Callback cb = component.annotation(name);
				if(cb != null) {
					TreeMap<Object, Object> map = new TreeMap<Object, Object>();
					map.put("direct", cb.direct());
					map.put("getter", cb.getter());
					map.put("setter", cb.setter());
					ret.put(name, map);
				}
			}
			return new Object[]{ret};
		}
		else return machine.invoke(address, method, params);
	}
	public void runSynchronized() {
		if(asyncInvokeOnComponent != null) {
			if(OCARM.instance.shouldTraceInvocations()) traceInvoke("async invoke component "+asyncInvokeOnComponent, asyncInvokeFunc, asyncInvokeParams, false);
			try {
				asyncReplyBuffer = hardwareInvoke(asyncInvokeOnComponent, asyncInvokeFunc, asyncInvokeParams);
				asyncReplyResult = Integer.valueOf(OCARM.INVOKE_SUCCESS);
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply(asyncReplyResult, asyncReplyBuffer);
			}
			// should be impossible; if it happens, we'll just have to wait for the next runSynchronized()
			catch(LimitReachedException e) { if(OCARM.instance.shouldTraceInvocations()) traceInvokeLimitReached(); }
			catch(NoSuchMethodException e) {
				asyncReplyResult = Integer.valueOf(OCARM.INVOKE_UNKNOWN_METHOD);
				asyncReplyBuffer = new Object[]{};
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply(asyncReplyResult, asyncReplyBuffer);
			}
			catch(IllegalArgumentException e) {
				asyncReplyResult = Integer.valueOf(OCARM.INVOKE_UNKNOWN_RECEIVER);
				asyncReplyBuffer = new Object[]{};
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply(asyncReplyResult, asyncReplyBuffer);
			}
			catch(Exception e) {
				e.printStackTrace();
				asyncReplyResult = Integer.valueOf(OCARM.INVOKE_UNKNOWN_ERROR);
				asyncReplyBuffer = new Object[]{e.getLocalizedMessage()};
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply(asyncReplyResult, asyncReplyBuffer);
			}
			finally {
				asyncInvokeOnComponent = null;
				asyncInvokeFunc = null;
				asyncInvokeParams = null;
			}
		}
		if(syncInvokeOnComponent != null) {
			if(OCARM.instance.shouldTraceInvocations()) traceInvoke("sync invoke component "+syncInvokeOnComponent, syncInvokeFunc, syncInvokeParams, syncInvokeDiscardReply);
			try {
				Object[] reply = hardwareInvoke(syncInvokeOnComponent, syncInvokeFunc, syncInvokeParams);
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply((int)OCARM.INVOKE_SUCCESS, reply);
				if(!syncInvokeDiscardReply) {
					replyResult = Integer.valueOf(OCARM.INVOKE_SUCCESS);
					replyBuffer = reply;
				}
			}
			// should be impossible; if it happens, we'll just have to wait for the next runSynchronized()
			catch(LimitReachedException e) { if(OCARM.instance.shouldTraceInvocations()) traceInvokeLimitReached(); }
			catch(NoSuchMethodException e) {
				if(OCARM.instance.shouldTraceInvocations())traceInvokeReply((int)OCARM.INVOKE_UNKNOWN_METHOD, new Object[]{});
				if(!syncInvokeDiscardReply) {
					replyResult = Integer.valueOf(OCARM.INVOKE_UNKNOWN_METHOD);
					replyBuffer = new Object[]{};
				}
			}
			catch(IllegalArgumentException e) {
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply((int)OCARM.INVOKE_UNKNOWN_RECEIVER, new Object[]{});
				if(!syncInvokeDiscardReply) {
					replyResult = Integer.valueOf(OCARM.INVOKE_UNKNOWN_RECEIVER);
					replyBuffer = new Object[]{};
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				if(OCARM.instance.shouldTraceInvocations()) traceInvokeReply((int)OCARM.INVOKE_UNKNOWN_ERROR, new Object[]{e.toString()});
				if(!syncInvokeDiscardReply) {
					replyResult = Integer.valueOf(OCARM.INVOKE_UNKNOWN_ERROR);
					replyBuffer = new Object[]{e.toString()};
				}
			}
			finally {
				syncInvokeOnComponent =null;
				syncInvokeFunc = null;
				syncInvokeParams = null;
			}
		}
	}

	/**
	 * The "cursor" when reading/writing objects.
	 */
	private transient int ptr;

	CP3(CPU cpu, Machine machine, JARMArchitecture parent) { super(cpu); this.machine = machine; this.vm = cpu.getVirtualMemorySpace(); this.parent = parent; }

	private boolean tryGetSignal() {
		if(signalBufferName != null) return true;
		Signal sig = machine.popSignal();
		if(sig == null) return false;
		else {
			signalBufferName = sig.name();
			signalBufferArgs = sig.args();
			return true;
		}
	}

	@Override
	public void storeCoprocessorRegisterToMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!cpu.isPrivileged()) throw new UndefinedException();
		switch(CRd) {
		case 0: {
			/* Signal Buffer */
			if(!tryGetSignal()) {
				cpu.setConditionV(true);
				return;
			}
			cpu.setConditionV(false);
			ptr = base;
			writeInterchangeValue(signalBufferName, icr_INTERCHANGE_PACKED);
			int rem = interchangeStoreTruncationRegister;
			for(Object o : signalBufferArgs) {
				if(rem-- == 1) break; // counterintuitive, but works
				writeInterchangeValue(o, icr_INTERCHANGE_PACKED);
			}
			writeTag(OCARM.ICTAG_END, icr_INTERCHANGE_PACKED);
			signalBufferName = null;
			signalBufferArgs = null;
			return;
		}
		case 1: {
			/* Reply Buffer */
			cpu.setConditionN(replyResult.intValue() != OCARM.INVOKE_SUCCESS);
			ptr = base;
			// technically not a tag...
			writeTag(replyResult.shortValue(), icr_INTERCHANGE_PACKED);
			int rem = interchangeStoreTruncationRegister;
			for(Object o : replyBuffer) {
				if(rem-- == 1) break; // counterintuitive, but works
				writeInterchangeValue(o, icr_INTERCHANGE_PACKED);
			}
			writeTag(OCARM.ICTAG_END, icr_INTERCHANGE_PACKED);
			return;
		}
		case 2: {
			/* Asynchronous Reply Buffer */
			if(asyncReplyBuffer == null)
				cpu.setConditionV(true);
			else {
				cpu.setConditionV(false);
				cpu.setConditionN(asyncReplyResult.intValue() != OCARM.INVOKE_SUCCESS);
				ptr = base;
				writeTag(asyncReplyResult.shortValue(), icr_INTERCHANGE_PACKED);
				int rem = interchangeStoreTruncationRegister;
				for(Object o : asyncReplyBuffer) {
					if(rem-- == 1) break; // counterintuitive, but works
					writeInterchangeValue(o, icr_INTERCHANGE_PACKED);
				}
				writeTag(OCARM.ICTAG_END, icr_INTERCHANGE_PACKED);
				return;
			}
			return;
		}
		case 3: {
			/* Component List Buffer */
			if(componentListBuffer == null) throw new UndefinedException();
			ptr = base;
			for(Object o : componentListBuffer) {
				writeInterchangeValue(o, icr_INTERCHANGE_PACKED);
			}
			writeTag(OCARM.ICTAG_END, icr_INTERCHANGE_PACKED);
			return;
		}
		case 4: {
			/* Compact Component */
			if(componentListBuffer == null) throw new UndefinedException();
			int i = compactComponentIndexRegister * 2;
			if(i >= componentListBuffer.length) {
				cpu.setConditionZ(true);
				cpu.setConditionV(false);
				return;
			}
			cpu.setConditionZ(false);
			UUID uuid = (UUID)componentListBuffer[i];
			String name = (String)componentListBuffer[i+1];
			boolean validString = true;
			if(name.length() >= 16) validString = false;
			else {
				ByteBuffer byteBuf = ioByteBuffer.get();
				byteBuf.clear();
				for(int n = 0; n < name.length(); ++n) {
					char c = name.charAt(n);
					if(c == 0 || c > 0x7F) { validString = false; break; }
					byteBuf.put((byte)c);
				}
				if(validString) {
					for(int n = name.length(); n < 16; ++n) {
						byteBuf.put((byte)0);
					}
					byteBuf.flip();
					writeBinaryUUID(base, true, uuid);
					vm.writeLong(base+16, byteBuf.getLong(), true, true);
					vm.writeLong(base+24, byteBuf.getLong(), true, true);
				}
			}
			cpu.setConditionV(!validString);
			return;
		}
		case 5: {
			/* Reply Buffer IO */
			cpu.setConditionN(replyResult.intValue() != OCARM.INVOKE_SUCCESS);
			if(replyResult.intValue() == OCARM.INVOKE_SUCCESS && replyBuffer.length == 1 && replyBuffer[0] instanceof byte[]) {
				cpu.setConditionC(true);
				byte[] bwat = (byte[])replyBuffer[0];
				ptr = base;
				writeByteArray(bwat, icr_INTERCHANGE_PACKED, bwat.length);
			} else cpu.setConditionC(false);
			return;
		}
		case 6: {
			/* Asynchronous Reply Buffer IO */
			if(asyncReplyBuffer == null)
				cpu.setConditionV(true);
			else {
				cpu.setConditionV(false);
				cpu.setConditionN(asyncReplyResult.intValue() != OCARM.INVOKE_SUCCESS);
				if(asyncReplyResult.intValue() == OCARM.INVOKE_SUCCESS && asyncReplyBuffer.length == 1 && asyncReplyBuffer[0] instanceof byte[]) {
					cpu.setConditionC(true);
					byte[] bwat = (byte[])asyncReplyBuffer[0];
					ptr = base;
					writeByteArray(bwat, icr_INTERCHANGE_PACKED, bwat.length);
				} else cpu.setConditionC(false);
			}
			return;
		}
		case 7: {
			/* Node Address */
			writeBinaryUUID(base, cpu.inStrictAlignMode(), UUID.fromString(machine.node().address()));
			cpu.setConditionZ(true);
			return;
		}
		case 8: {
			/* Temporary Address */
			String addr = machine.tmpAddress();
			if(addr == null) {
				writeBinaryUUID(base, cpu.inStrictAlignMode(), new UUID(0,0));
				cpu.setConditionZ(false);
			}
			else {
				writeBinaryUUID(base, cpu.inStrictAlignMode(), UUID.fromString(addr));
				cpu.setConditionZ(true);
			}
			return;
		}
		case 9: {
			/* Battery Status */
			/* TODO: handle infinite power */
			cpu.setConditionZ(true);
			Connector connector = (Connector)machine.node();
			double energy = connector.globalBuffer();
			double max = connector.globalBufferSize();
			vm.writeLong(base, Double.doubleToLongBits(energy), cpu.inStrictAlignMode(), true);
			vm.writeLong(base+8, Double.doubleToLongBits(max), cpu.inStrictAlignMode(), true);
		}
		}
		throw new UndefinedException();
	}

	@Override
	public void loadCoprocessorRegisterFromMemory(boolean unconditional,
			int coproc, boolean D, int base, int CRd, int option, int iword)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!cpu.isPrivileged()) throw new UndefinedException();
		switch(CRd) {
		case 0: {
			/* Signal Buffer */
			ptr = base;
			Object name = readInterchangeValue(icr_INTERCHANGE_PACKED);
			if(!(name instanceof String)) throw new UndefinedException();
			ArrayList<Object> args = new ArrayList<Object>();
			Object obj;
			do {
				obj = readInterchangeValue(icr_INTERCHANGE_PACKED);
				if(obj != END_SENTINEL) args.add(obj);
			} while(obj != END_SENTINEL);
			cpu.setConditionV(machine.signal((String)name, args.toArray()));
			return;
		}
		case 1: {
			/* Invoke Buffer */
			ptr = base;
			Object name = readInterchangeValue(icr_INTERCHANGE_PACKED);
			if(!(name instanceof String)) throw new UndefinedException();
			invokeBufferParams = readArray(icr_INTERCHANGE_PACKED);
			/* if no exception was thrown, one won't be thrown here */
			invokeBufferFunc = (String)name;
			return;
		}
		case 2: {
			/* Asynchronous Invoke Buffer */
			ptr = base;
			Object name = readInterchangeValue(icr_INTERCHANGE_PACKED);
			if(!(name instanceof String)) throw new UndefinedException();
			asyncInvokeBufferParams = readArray(icr_INTERCHANGE_PACKED);
			/* if no exception was thrown, one won't be thrown here */
			asyncInvokeBufferFunc = (String)name;
			return;
		}
		case 15: {
			/* Crash Buffer */
			assert(OCARM.MAX_STRING_LENGTH >= 1024);
			ByteBuffer byteBuf = ioByteBuffer.get();
			byteBuf.clear();
			try {
				for(int n = 0; n < 1024; ++n) {
					byte b = vm.readByte(base++);
					if(b == 0) break;
					else byteBuf.put(b);
				}
			}
			catch(BusErrorException e) { /* do nothing*/ }
			catch(EscapeRetryException e) { /* do nothing*/ }
			byteBuf.flip();
			CharsetDecoder decoder = utf8Decoder.get();
			CharBuffer charBuf = stringCharBuffer.get();
			charBuf.clear();
			decoder.decode(byteBuf, charBuf, true);
			decoder.flush(charBuf);
			decoder.reset();
			charBuf.flip();
			overrideResult = new ExecutionResult.Error(charBuf.toString());
			if(OCARM.instance.shouldTraceInvocations()) {
				OCARM.logger.error("Crash Buffer loaded, error was: %s", charBuf);
				cpu.dumpState(System.err);
			}
			throw new EscapeCompleteException();
		}
		}
		throw new UndefinedException();
	}

	@Override
	public void moveCoreRegisterToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!cpu.isPrivileged()) throw new UndefinedException();
		switch(CRn) {
		case 0:
			switch(CRm) {
			case 0:
				/* Memory Module Index Register */
				memoryModuleIndexRegister = cpu.readRegister(Rt);
				return;
			}
			break;
		case 1:
			switch(CRm) {
			case 0:
				/* Interchange Control Register */
				icr_INTERCHANGE_PACKED = (cpu.readRegister(Rt)&1) != 0;
				return;
			case 1:
				/* Invoke Target Register */
				invokeTargetRegister = cpu.readRegister(Rt);
				return;
			case 2:
				/* Compact Component Index Register */
				compactComponentIndexRegister = cpu.readRegister(Rt);
				return;
			case 3:
				/* Interchange Store Truncation Register */
				interchangeStoreTruncationRegister = cpu.readRegister(Rt);
				return;
			}
			break;
		case 4:
			switch(CRm) {
			case 0: {
				if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Boring sleep!");
				if(tryGetSignal()) return;
				int sleepTicks = cpu.readRegister(Rt);
				if(sleepTicks > 0) {
					if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Will sleep for %d", sleepTicks);
					overrideResult = new ExecutionResult.Sleep(sleepTicks);
					throw new EscapeCompleteException();
				}
				return;
			}
			}
			break;
		}
		throw new UndefinedException();
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegister(boolean unconditional,
			int coproc, int opc1, int opc2, int CRn, int CRm, int Rt)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!cpu.isPrivileged()) throw new UndefinedException();
		switch(CRn) {
		case 0:
			switch(CRm) {
			case 0:
				/* Memory Module Size Register */
				cpu.writeRegister(Rt, parent.getMemorySize(memoryModuleIndexRegister));
				return;
			case 1: {
				/* Memory Module Latency Register */
				cpu.writeRegister(Rt, parent.getMemoryLatency(memoryModuleIndexRegister));
				return;
			}
			case 2: {
				/* Central Processor Speed Register */
				cpu.writeRegister(Rt, parent.getCPUCyclesPerTick());
				return;	
			}
			}
			break;
		case 1:
			switch(CRm) {
			case 0:
				/* Interchange Control Register */
				cpu.writeRegister(Rt, icr_INTERCHANGE_PACKED ? 1 : 0);
				return;
			case 1:
				/* Invoke Target Register */
				cpu.writeRegister(Rt, invokeTargetRegister);
				return;
			case 2:
				/* Compact Component Index Register */
				cpu.writeRegister(Rt, compactComponentIndexRegister);
				return;
			case 3:
				/* Interchange Store Truncation Register */
				cpu.writeRegister(Rt, interchangeStoreTruncationRegister);
				return;
			}
			break;
		case 2:
			switch(CRm) {
			case 0: {
				/* Signal Size Register */
				if(!tryGetSignal()) {
					cpu.setConditionV(true);
					cpu.writeRegister(Rt, 0);
				}
				else {
					cpu.setConditionV(false);
					int size = sizeofInterchangeValue(signalBufferName, icr_INTERCHANGE_PACKED); // signal name
					int rem = interchangeStoreTruncationRegister;
					for(Object o : signalBufferArgs) {
						if(rem-- == 1) break; // counterintuitive, but works
						size += sizeofInterchangeValue(o, icr_INTERCHANGE_PACKED);
					}
					size += icr_INTERCHANGE_PACKED ? 2 : 4; // END
					cpu.writeRegister(Rt, size);
				}
				return;
			}
			case 1: {
				/* Reply Size Register */
				cpu.setConditionN(replyResult.intValue() != OCARM.INVOKE_SUCCESS);
				int size = icr_INTERCHANGE_PACKED ? 2 : 4; // result
				int rem = interchangeStoreTruncationRegister;
				for(Object o : replyBuffer) {
					if(rem-- == 1) break; // counterintuitive, but works
					size += sizeofInterchangeValue(o, icr_INTERCHANGE_PACKED);
				}
				size += icr_INTERCHANGE_PACKED ? 2 : 4; // END
				cpu.writeRegister(Rt, size);
				return;
			}
			case 2: {
				/* Asynchronous Reply Size Register */
				if(asyncReplyBuffer == null) {
					cpu.setConditionV(true);
					cpu.writeRegister(Rt, 0);
				}
				else {
					cpu.setConditionV(false);
					cpu.setConditionN(asyncReplyResult.intValue() != OCARM.INVOKE_SUCCESS);
					int size = icr_INTERCHANGE_PACKED ? 2 : 4; // result
					int rem = interchangeStoreTruncationRegister;
					for(Object o : asyncReplyBuffer) {
						if(rem-- == 1) break; // counterintuitive, but works
						size += sizeofInterchangeValue(o, icr_INTERCHANGE_PACKED);
					}
					size += icr_INTERCHANGE_PACKED ? 2 : 4; // END
					cpu.writeRegister(Rt, size);
					return;
				}
			}
			case 3: {
				/* Component List Size Register */
				if(componentListBuffer == null) throw new UndefinedException();
				cpu.writeRegister(Rt, sizeofInterchangeValue(componentListBuffer, icr_INTERCHANGE_PACKED) - 2);
				return;
			}
			case 5: {
				/* Reply Buffer IO Size Register */
				cpu.setConditionN(replyResult.intValue() != OCARM.INVOKE_SUCCESS);
				if(replyResult.intValue() == OCARM.INVOKE_SUCCESS && replyBuffer.length == 1 && replyBuffer[0] instanceof byte[]) {
					cpu.setConditionC(true);
					cpu.writeRegister(Rt, ((byte[])replyBuffer[0]).length);
				}
				else {
					cpu.setConditionC(false);
					cpu.writeRegister(Rt, 0);
				}
				return;
			}
			case 6: {
				/* Asynchronous Reply Buffer IO Size Register */
				if(asyncReplyBuffer == null) {
					cpu.setConditionV(true);
					cpu.writeRegister(Rt, 0);
				}
				else {
					cpu.setConditionV(false);
					cpu.setConditionN(asyncReplyResult.intValue() != OCARM.INVOKE_SUCCESS);
					if(asyncReplyResult.intValue() == OCARM.INVOKE_SUCCESS && asyncReplyBuffer.length == 1 && asyncReplyBuffer[0] instanceof byte[]) {
						cpu.setConditionC(true);
						cpu.writeRegister(Rt, ((byte[])asyncReplyBuffer[0]).length);
					}
					else {
						cpu.setConditionC(false);
						cpu.writeRegister(Rt, 0);
					}
				}
				return;
			}
			}
			break;
		}
		throw new UndefinedException();
	}

	@Override
	public void coprocessorDataOperation(boolean unconditional, int coproc,
			int opc1, int opc2, int CRn, int CRm, int CRd)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!cpu.isPrivileged()) throw new UndefinedException();
		switch(opc1) {
		case 0:
			/* Shut Down */
			overrideResult = new ExecutionResult.Shutdown(false);
			throw new EscapeCompleteException();
		case 1:
			/* Reboot */
			overrideResult = new ExecutionResult.Shutdown(true);
			throw new EscapeCompleteException();
		case 2: {
			/* Invoke */
			boolean discardReply = (opc2 & 1) != 0;
			boolean nonBlocking = (opc2 & 2) != 0;
			boolean valueTarget = (opc2 & 4) != 0;
			if(valueTarget) throw new UndefinedException(); /* TODO: implement me */
			assert((opc2 & ~7) == 0); /* opc2 *should* never have such a value */
			if(invokeBufferFunc == null) throw new UndefinedException();
			assert(invokeBufferParams != null);
			String addr = readBinaryUUID(invokeTargetRegister, cpu.inStrictAlignMode());
			/* We reimplement much of machine.invoke() here for error handling purposes */
			Node us = machine.node();
			if(us == null || us.network() == null) throw new EscapeRetryException();
			Node node = us.network().node(addr);
			Integer result = null;
			Object[] reply = null;
			boolean needSynchronizedCall = false;
			if(OCARM.instance.shouldTraceInvocations()) traceInvoke("direct invoke component "+addr, invokeBufferFunc, invokeBufferParams, discardReply);
			if(node == null || !node.canBeReachedFrom(us) || !(node instanceof Component))
				result = Integer.valueOf(OCARM.INVOKE_UNKNOWN_RECEIVER);
			else {
				Component component = (Component)node;
				try {
					if(isFakeMethod(invokeBufferFunc)) {
						reply = hardwareInvoke(addr, invokeBufferFunc, invokeBufferParams);
						result = Integer.valueOf(OCARM.INVOKE_SUCCESS);
					}
					else {
						Callback callback = component.annotation(invokeBufferFunc);
						if(callback == null) result = Integer.valueOf(OCARM.INVOKE_UNKNOWN_METHOD);
						else if(!callback.direct()) {
							if(nonBlocking)
								result = Integer.valueOf(OCARM.INVOKE_INDIRECT_REQUIRED);
							else needSynchronizedCall = true;
						}
						else {
							reply = hardwareInvoke(addr, invokeBufferFunc, invokeBufferParams);
							result = Integer.valueOf(OCARM.INVOKE_SUCCESS);
						}
					}
				}
				catch(IllegalArgumentException e) {
					/* this can happen if the component is removed from the network between our check and the actual call */
					result = Integer.valueOf(OCARM.INVOKE_UNKNOWN_RECEIVER);
				}
				catch(NoSuchMethodException e) {
					result = Integer.valueOf(OCARM.INVOKE_UNKNOWN_METHOD);
				}
				catch(LimitReachedException e) {
					if(OCARM.instance.shouldTraceInvocations()) traceInvokeLimitReached();
					if(nonBlocking)
						result = Integer.valueOf(OCARM.INVOKE_LIMIT_REACHED);
					else needSynchronizedCall = true;
				}
				catch(Exception e) {
					e.printStackTrace();
					result = Integer.valueOf(OCARM.INVOKE_UNKNOWN_ERROR);
					reply = new Object[]{e.getLocalizedMessage()};
				}
			}
			if(OCARM.instance.shouldTraceInvocations()) if(!needSynchronizedCall) traceInvokeReply(result, reply);
			if(needSynchronizedCall) {
				syncInvokeDiscardReply = discardReply;
				syncInvokeOnComponent = addr;
				syncInvokeFunc = invokeBufferFunc;
				syncInvokeParams = invokeBufferParams;
				if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Invocation is escalating to synchronized");
				throw new EscapeCompleteException();
			}
			else if(!discardReply) {
				replyResult = result;
				if(reply != null) replyBuffer = reply;
				else replyBuffer = new Object[]{};
			}
			return;
		}
		case 3: {
			/* Asynchronous Invoke */
			boolean copyInvoke = (opc2 & 1) != 0;
			boolean valueTarget = (opc2 & 4) != 0;
			if(valueTarget) throw new UndefinedException(); /* TODO: implement me */
			if((opc2 & ~5) != 0) throw new UndefinedException();
			if(copyInvoke) {
				asyncInvokeBufferFunc = invokeBufferFunc;
				asyncInvokeBufferParams = invokeBufferParams;
			}
			if(asyncInvokeBufferFunc == null) throw new UndefinedException();
			assert(asyncInvokeBufferParams != null);
			String addr = readBinaryUUID(invokeTargetRegister, cpu.inStrictAlignMode());
			asyncInvokeOnComponent = addr;
			asyncInvokeFunc = asyncInvokeBufferFunc;
			asyncInvokeParams = asyncInvokeBufferParams;
			asyncReplyResult = null;
			asyncReplyBuffer = null;
			return;
		}
		case 4: {
			/* NVRAM Flush */
			parent.flushNVRAM();
			return;
		}
		case 5: {
			/* Component List Buffer Latch */
			Map<String,String> components = machine.components();
			synchronized(components) {
				componentListBuffer = new Object[components.size()*2];
				int p = 0; /* Ick, am I capable of thinking in a language other than C? */
				for(Map.Entry<String, String> entry : components.entrySet()) {
					componentListBuffer[p++] = UUID.fromString(entry.getKey());
					componentListBuffer[p++] = entry.getValue();
				}
			}
			return;
		}
		case 6: {
			/* Signal Pop */
			tryGetSignal();
			cpu.setConditionV(signalBufferName == null);
			signalBufferName = null;
			signalBufferArgs = null;
			return;
		}
		case 7: {
			/* Dispose Value */
			parent.disposeValue(invokeTargetRegister, machine);
			return;
		}
		case 8: {
			/* Dispose All Values */
			parent.disposeAllValues(machine);
			return;
		}
		}
		throw new UndefinedException();
	}

	@Override
	public void moveCoreRegistersToCoprocessorRegister(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		if(!cpu.isPrivileged()) throw new UndefinedException();
		switch(CRm) {
		case 0: {
			if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Targeted sleep, world time!");
			if(tryGetSignal()) return;
			long curTime = machine.worldTime();
			long targetTime = (cpu.readRegister(Rt) & 0xFFFFFFFFL) | ((long)cpu.readRegister(Rt2) << 32);
			if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Target time: %d; Current time: %d", targetTime, curTime);
			if(targetTime <= curTime) return;
			int sleepTicks;
			if(targetTime - curTime > Integer.MAX_VALUE) sleepTicks = Integer.MAX_VALUE;
			else sleepTicks = (int)(targetTime - curTime);
			if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Will sleep for %d", sleepTicks);
			overrideResult = new ExecutionResult.Sleep(sleepTicks);
			throw new EscapeCompleteException();
		}
		case 2: {
			if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Targeted sleep, machine time!");
			if(tryGetSignal()) return;
			long curTime = (long)Math.floor(machine.upTime()*20+0.5);
			long targetTime = (cpu.readRegister(Rt) & 0xFFFFFFFFL) | ((long)cpu.readRegister(Rt2) << 32);
			if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Target time: %d; Current time: %d", targetTime, curTime);
			if(targetTime <= curTime) return;
			int sleepTicks;
			if(targetTime - curTime > Integer.MAX_VALUE) sleepTicks = Integer.MAX_VALUE;
			else sleepTicks = (int)(targetTime - curTime);
			if(OCARM.instance.shouldTraceInvocations()) OCARM.logger.info("Will sleep for %d", sleepTicks);
			overrideResult = new ExecutionResult.Sleep(sleepTicks);
			throw new EscapeCompleteException();
		}
		}
		throw new UndefinedException();
	}

	@Override
	public void moveCoprocessorRegisterToCoreRegisters(boolean unconditional,
			int coproc, int opc1, int CRm, int Rt, int Rt2)
			throws BusErrorException, AlignmentException, UndefinedException, EscapeRetryException, EscapeCompleteException {
		/* NOT privileged! */
		switch(CRm) {
		case 0: {
			/* World Clock Register */
			long world_time = machine.worldTime();
			cpu.writeRegister(Rt, (int)world_time);
			cpu.writeRegister(Rt2, (int)(world_time>>>32));
			return;
		}
		case 1: {
			/* Real Time Register */
			/* IS privileged! */
			if(!cpu.isPrivileged()) throw new UndefinedException();
			long now = System.currentTimeMillis();
			cpu.writeRegister(Rt, (int)now);
			cpu.writeRegister(Rt2, (int)(now>>>32));
			return;
		}
		case 2: {
			/* Up Time Register */
			long now = (long)Math.floor(machine.upTime() * 20 + 0.5);
			cpu.writeRegister(Rt, (int)now);
			cpu.writeRegister(Rt2, (int)(now>>>32));
			return;
		}
		case 3: {
			/* CPU Time Register */
			double cpuTime = machine.cpuTime();
			long bits = Double.doubleToLongBits(cpuTime);
			cpu.writeRegister(Rt, (int)bits);
			cpu.writeRegister(Rt2, (int)(bits>>>32));
			return;
		}
		}
		throw new UndefinedException();
	}

	private static final ThreadLocal<CharsetDecoder> utf8Decoder = new ThreadLocal<CharsetDecoder>() {
		@Override
		protected CharsetDecoder initialValue() {
			CharsetDecoder ret = Charset.forName("UTF-8").newDecoder();
			ret.onMalformedInput(CodingErrorAction.REPLACE);
			ret.onUnmappableCharacter(CodingErrorAction.REPLACE);
			return ret;
		}
	};
	private static final ThreadLocal<CharsetEncoder> utf8Encoder = new ThreadLocal<CharsetEncoder>() {
		@Override
		protected CharsetEncoder initialValue() {
			CharsetEncoder ret = Charset.forName("UTF-8").newEncoder();
			ret.onMalformedInput(CodingErrorAction.REPLACE);
			ret.onUnmappableCharacter(CodingErrorAction.REPLACE);
			return ret;
		}
	};
	private static final ThreadLocal<ByteBuffer> ioByteBuffer = new ThreadLocal<ByteBuffer>() {
		@Override
		protected ByteBuffer initialValue() {
			return ByteBuffer.allocate(OCARM.padToWordLength(Math.max(OCARM.MAX_STRING_LENGTH, OCARM.MAX_BYTE_ARRAY_LENGTH)));
		}
	};
	private static final ThreadLocal<CharBuffer> stringCharBuffer = new ThreadLocal<CharBuffer>() {
		@Override
		protected CharBuffer initialValue() {
			return CharBuffer.allocate(OCARM.MAX_STRING_LENGTH);
		}
	};
	
	private Map<Object,Object> readCompound(boolean packed) throws AlignmentException, BusErrorException, UndefinedException, EscapeRetryException {
		TreeMap<Object,Object> ret = new TreeMap<Object,Object>();
		do {
			Object key = readInterchangeValue(packed);
			if(key == END_SENTINEL) break;
			if(key == null || key instanceof byte[] || key instanceof Map || key instanceof Object[])
				throw new UndefinedException();
			Object value = readInterchangeValue(packed);
			if(value == END_SENTINEL) throw new UndefinedException();
		} while(true);
		return ret;
	}

	private Object[] readArray(boolean packed) throws AlignmentException, BusErrorException, UndefinedException, EscapeRetryException {
		LinkedList<Object> ret = new LinkedList<Object>();
		do {
			Object next = readInterchangeValue(packed);
			if(next == END_SENTINEL) break;
			else ret.add(next);
		} while(true);
		return ret.toArray();
	}
	
	private void writeTag(short tag, boolean packed) throws AlignmentException, BusErrorException, UndefinedException, EscapeRetryException {
		if(packed) {
			vm.writeShort(ptr, tag, false, true);
			ptr += 2;
		}
		else {
			vm.writeInt(ptr, tag, true, true);
			ptr += 4;
		}
	}
	private void writeByteArray(byte[] bwat, boolean unaligned, int length) throws AlignmentException, BusErrorException, UndefinedException, EscapeRetryException {
		if(unaligned) {
			for(int n = 0; n < length; ++n) {
				vm.writeByte(ptr++, bwat[n]);
			}
		}
		else {
			int n;
			for(n = 0; n < length-3; n += 4) {
				int wut = (bwat[n] << 24) | ((bwat[n+1]&255) << 16) | ((bwat[n+2]&255) << 8) | (bwat[n+3]&255);
				vm.writeInt(ptr, wut, true, true);
				ptr += 4;
			}
			if(n != length) {
				switch(length&3) {
				// case 0 is impossible
				case 1: vm.writeInt(ptr, bwat[n] << 24, true, true); break;
				case 2: vm.writeInt(ptr, (bwat[n] << 24) | ((bwat[n+1]&255) << 16), true, true); break;
				case 3: vm.writeInt(ptr, (bwat[n] << 24) | ((bwat[n+1]&255) << 16) | ((bwat[n+2]&255) << 8), true, true); break;
				}
				ptr += 4;
			}
		}
	}
	private void writeInterchangeValue(Object wat, boolean packed) throws AlignmentException, BusErrorException, UndefinedException, EscapeRetryException {
		if(wat == null)
			writeTag(OCARM.ICTAG_NULL, packed);
		else if(wat instanceof byte[]) {
			byte[] bwat = (byte[])wat;
			if(bwat.length > OCARM.MAX_BYTE_ARRAY_LENGTH) {
				OCARM.logger.error("Byte array was too long! This shouldn't happen! (Did you set an unreasonably huge buffer size in your config? Don't let them get above "+OCARM.MAX_BYTE_ARRAY_LENGTH+") Acting unstable!");
				throw new UndefinedException();
			}
			writeTag((short)(OCARM.ICTAG_BYTE_ARRAY + bwat.length), packed);
			writeByteArray(bwat, packed, bwat.length);
		}
		else if(wat instanceof String) {
			String swat = (String)wat;
			if(swat.length() == 36) {
				try {
					UUID uuid = UUID.fromString(swat);
					writeInterchangeValue(uuid, packed);
					return;
				}
				catch(IllegalArgumentException e) { /* continue */ }
			}
			CharBuffer charBuf = stringCharBuffer.get();
			charBuf.clear();
			charBuf.put(swat);
			charBuf.flip();
			CharsetEncoder encoder = utf8Encoder.get();
			ByteBuffer byteBuf = ioByteBuffer.get();
			byteBuf.clear();
			CoderResult result = encoder.encode(charBuf, byteBuf, true);
			if(result == CoderResult.OVERFLOW || encoder.flush(byteBuf) == CoderResult.OVERFLOW) {
				OCARM.logger.error("String was too long! This shouldn't happen! Acting unstable!");
				encoder.reset();
				throw new UndefinedException();
			}
			encoder.reset();
			byteBuf.flip();
			int len = byteBuf.limit();
			assert(len <= OCARM.MAX_STRING_LENGTH);
			writeTag((short)(OCARM.ICTAG_STRING + len), packed);
			if(byteBuf.hasArray() && byteBuf.arrayOffset() == 0)
				writeByteArray(byteBuf.array(), packed, len);
			else {
				byte[] temp = new byte[len];
				byteBuf.get(temp);
				writeByteArray(temp, packed, len);
			}
		}
		else if(wat instanceof UUID) {
			UUID uwat = (UUID)wat;
			writeTag(OCARM.ICTAG_UUID, packed);
			vm.writeLong(ptr, uwat.getMostSignificantBits(), packed, true);
			vm.writeLong(ptr+8, uwat.getLeastSignificantBits(), packed, true);
			ptr += 16;
		}
		else if(wat instanceof Map<?,?>) {
			Map<?,?> mwat = (Map<?,?>)wat;
			writeTag(OCARM.ICTAG_COMPOUND, packed);
			for(Object oent : mwat.entrySet()) {
				Map.Entry<?,?> ent = (Map.Entry<?,?>)oent;
				writeInterchangeValue(ent.getKey(), packed);
				writeInterchangeValue(ent.getValue(), packed);
			}
			writeTag(OCARM.ICTAG_END, packed);
		}
		else if(wat instanceof Object[]) {
			writeTag(OCARM.ICTAG_ARRAY, packed);
			for(Object el : (Object[])wat) {
				writeInterchangeValue(el, packed);
			}
			writeTag(OCARM.ICTAG_END, packed);
		}
		else if(wat instanceof Integer) {
			writeTag(OCARM.ICTAG_INT, packed);
			vm.writeInt(ptr, ((Integer)wat).intValue(), packed, true);
			ptr += 4;
		}
		else if(wat instanceof Number) {
			double dwat = ((Number)wat).doubleValue();
			/* TODO: do this as a preprocessing step instead of here */
			int iwat = (int)dwat;
			if((double)iwat == dwat) {
				writeTag(OCARM.ICTAG_INT, packed);
				vm.writeInt(ptr, iwat, packed, true);
				ptr += 4;
			}
			else {
				writeTag(OCARM.ICTAG_DOUBLE, packed);
				vm.writeLong(ptr, Double.doubleToRawLongBits(dwat), packed, true);
				ptr += 8;
			}
		}
		else if(wat instanceof Boolean) {
			writeTag(OCARM.ICTAG_BOOLEAN, packed);
			if(packed) {
				vm.writeByte(ptr++, ((Boolean)wat).booleanValue() ? (byte)-1 : (byte)0);
			}
			else {
				vm.writeInt(ptr, ((Boolean)wat).booleanValue() ? -1 : 0, true, true);
				ptr += 4;
			}
		}
		else if(wat instanceof Value) {
			writeTag(OCARM.ICTAG_VALUE, packed);
			vm.writeInt(ptr, parent.mapValue((Value)wat), packed, true);
			ptr += 4;
		}
		else if(wat instanceof Character) {
			writeInterchangeValue(String.valueOf(((Character)wat).charValue()), packed);
		}
		/* TODO: NBTTagCompound */
		else {
			OCARM.logger.error("writeInterchangeValue() called on incompatible type: "+wat.getClass().getName());
			throw new UndefinedException();
		}
	}
	private int sizeofInterchangeValue(Object wat, boolean packed) throws UndefinedException {
		int length;
		if(wat == null)
			length = 0;
		else if(wat instanceof byte[]) {
			length = ((byte[])wat).length;
			if(!packed) length = OCARM.padToWordLength(length);
		}
		else if(wat instanceof String) {
			String swat = (String)wat;
			if(swat.length() == 36) {
				try {
					UUID uuid = UUID.fromString(swat);
					return sizeofInterchangeValue(uuid, packed);
				}
				catch(IllegalArgumentException e) { /* continue */ }
			}
			CharBuffer charBuf = stringCharBuffer.get();
			charBuf.clear();
			charBuf.put(swat);
			charBuf.flip();
			CharsetEncoder encoder = utf8Encoder.get();
			ByteBuffer byteBuf = ioByteBuffer.get();
			byteBuf.clear();
			CoderResult result = encoder.encode(charBuf, byteBuf, true);
			if(result == CoderResult.OVERFLOW || encoder.flush(byteBuf) == CoderResult.OVERFLOW) {
				OCARM.logger.error("String was too long! This shouldn't happen! Acting unstable!");
				encoder.reset();
				throw new UndefinedException();
			}
			encoder.reset();
			byteBuf.flip();
			length = byteBuf.limit();
			assert(length <= OCARM.MAX_STRING_LENGTH);
			if(!packed) length = OCARM.padToWordLength(length);
		}
		else if(wat instanceof UUID) {
			length = 16;
		}
		else if(wat instanceof Map) {
			Map<?,?> mwat = (Map<?,?>)wat;
			length = icr_INTERCHANGE_PACKED ? 2 : 4;
			for(Map.Entry<?,?> ent : mwat.entrySet()) {
				length += sizeofInterchangeValue(ent.getKey(), packed);
				length += sizeofInterchangeValue(ent.getValue(), packed);
			}
		}
		else if(wat instanceof Object[]) {
			length = icr_INTERCHANGE_PACKED ? 2 : 4;
			for(Object el : (Object[])wat) {
				length += sizeofInterchangeValue(el, packed);
			}
		}
		else if(wat instanceof Integer) {
			length = 4;
		}
		else if(wat instanceof Number) {
			double dwat = ((Number)wat).doubleValue();
			/* TODO: flag to control this */
			int iwat = (int)dwat;
			if((double)iwat == dwat) {
				length = 4;
			}
			else {
				length = 8;
			}
		}
		else if(wat instanceof Boolean) {
			length = packed ? 1 : 4;
		}
		else if(wat instanceof Value) {
			length = 4;
		}
		else if(wat instanceof Character) {
			return sizeofInterchangeValue(String.valueOf(((Character)wat).charValue()), packed);
		}
		/* TODO: NBTTagCompound */
		else {
			OCARM.logger.error("sizeofInterchangeValue() called on incompatible type: "+wat.getClass().getName());
			throw new UndefinedException();
		}
		return length + (icr_INTERCHANGE_PACKED ? 2 : 4);
	}

	private Object readInterchangeValue(boolean packed) throws AlignmentException, BusErrorException, UndefinedException, EscapeRetryException {
		Object ret;
		int tag;
		if(packed) {
			tag = vm.readShort(ptr, false, true);
			ptr += 2;
		}
		else {
			tag = vm.readInt(ptr, true, true);
			ptr += 4;
		}
		int skip;
		if(tag >= 0) {
			if(tag > 0x8000) {
				throw new UndefinedException(); /* bad tag! */
			}
			else if((tag & 0x4000) != 0 && (tag & 0x3FFF) <= OCARM.MAX_BYTE_ARRAY_LENGTH) {
				if(packed) ret = readUnalignedByteArray(ptr, tag&0x3FFF);
				else ret = readAlignedByteArray(ptr, tag&0x3FFF);
				skip = tag & 0x3FFF;
			}
			else if(tag <= OCARM.MAX_STRING_LENGTH) {
				/* String */
				if(packed) ret = readUnalignedString(ptr, tag);
				else ret = readAlignedString(ptr, tag);
				skip = tag & 0x3FFF;
			}
			else throw new UndefinedException(); /* too long! */
		}
		else {
			switch(tag) {
			case OCARM.ICTAG_VALUE:
				ret = parent.getValue(Integer.valueOf(vm.readInt(ptr, !packed, true)));
				skip = 4;
				break;
			case OCARM.ICTAG_UUID:
				ret = readBinaryUUID(ptr, !packed);
				skip = 16;
				break;
			case OCARM.ICTAG_COMPOUND:
				ret = readCompound(packed);
				skip = 0;
				break;
			case OCARM.ICTAG_ARRAY:
				ret = readArray(packed);
				skip = 0;
				break;
			case OCARM.ICTAG_INT:
				ret = Integer.valueOf(vm.readInt(ptr, !packed, true));
				skip = 4;
				break;
			case OCARM.ICTAG_DOUBLE:
				ret = Double.valueOf(Double.longBitsToDouble(vm.readLong(ptr, !packed, true)));
				skip = 8;
				break;
			case OCARM.ICTAG_BOOLEAN:
				if(packed) {
					ret = Boolean.valueOf(vm.readByte(ptr) != 0);
					skip = 1;
				}
				else {
					ret = Boolean.valueOf(vm.readInt(ptr, true, true) != 0);
					skip = 4;
				}
				break;
			case OCARM.ICTAG_NULL:
				ret = null;
				skip = 0;
				break;
			case OCARM.ICTAG_END:
				ret = END_SENTINEL;
				skip = 0;
				break;
			default: throw new UndefinedException(); /* unknown tag! */
			}
		}
		if(packed) ptr += skip;
		else ptr += OCARM.padToWordLength(skip);
		return ret;
	}
	
	private byte[] readUnalignedByteArray(int addr, int length) throws AlignmentException, BusErrorException, EscapeRetryException {
		ByteBuffer byteBuf = ioByteBuffer.get();
		byteBuf.clear();
		for(int n = 0; n < length; ++n) {
			byteBuf.put(vm.readByte(addr++));
		}
		byteBuf.flip();
		byte[] ret = new byte[length];
		byteBuf.get(ret);
		return ret;
	}
	
	private byte[] readAlignedByteArray(int addr, int length) throws AlignmentException, BusErrorException, EscapeRetryException {
		ByteBuffer byteBuf = ioByteBuffer.get();
		byteBuf.clear();
		for(int n = 0; n < length; n += 4) {
			byteBuf.putInt(vm.readInt(addr, true, true));
			addr += 4;
		}
		byteBuf.flip();
		byteBuf.limit(length);
		byte[] ret = new byte[length];
		byteBuf.get(ret);
		return ret;
	}
	
	private String readUnalignedString(int addr, int length) throws BusErrorException, EscapeRetryException {
		ByteBuffer byteBuf = ioByteBuffer.get();
		byteBuf.clear();
		for(int n = 0; n < length; ++n) {
			byteBuf.put(vm.readByte(addr++));
		}
		byteBuf.flip();
		CharsetDecoder decoder = utf8Decoder.get();
		CharBuffer charBuf = stringCharBuffer.get();
		charBuf.clear();
		decoder.decode(byteBuf, charBuf, true);
		decoder.flush(charBuf);
		decoder.reset();
		charBuf.flip();
		return charBuf.toString();
	}

	private String readAlignedString(int addr, int length) throws AlignmentException, BusErrorException, EscapeRetryException {
		/* we're sloppy and ignore weird results in here */
		ByteBuffer byteBuf = ioByteBuffer.get();
		byteBuf.clear();
		for(int n = 0; n < length; n += 4) {
			byteBuf.putInt(vm.readInt(addr, true, true));
			addr += 4;
		}
		byteBuf.flip();
		byteBuf.limit(length);
		CharsetDecoder decoder = utf8Decoder.get();
		CharBuffer charBuf = stringCharBuffer.get();
		charBuf.clear();
		decoder.decode(byteBuf, charBuf, true);
		decoder.flush(charBuf);
		decoder.reset();
		charBuf.flip();
		return charBuf.toString();
	}

	private String readBinaryUUID(int addr, boolean aligned) throws AlignmentException, BusErrorException, EscapeRetryException {
		return new UUID(vm.readLong(addr, aligned, true), vm.readLong(addr+8, aligned, true)).toString();
	}
	
	private void writeBinaryUUID(int addr, boolean aligned, UUID uuid) throws AlignmentException, BusErrorException, EscapeRetryException {
		vm.writeLong(addr, uuid.getMostSignificantBits(), aligned, true);
		vm.writeLong(addr+8, uuid.getLeastSignificantBits(), aligned, true);
	}
	
	private String interchangeObjectToString(Object wat) {
		if(wat == null) return "null";
		else if(wat instanceof byte[]) return String.format("%d-byte array", ((byte[])wat).length);
		else if(wat instanceof String) return String.format("string(\"%s\")", wat);
		else if(wat instanceof UUID) return String.format("uuid(%s)", wat);
		else if(wat instanceof Map<?,?>) {
			Map<?,?> mwat = (Map<?,?>)wat;
			if(mwat.isEmpty()) return "empty map";
			StringBuilder ret = new StringBuilder();
			ret.append("map(");
			for(Map.Entry<?,?> ent : mwat.entrySet()) {
				ret.append(interchangeObjectToString(ent.getKey()));
				ret.append("=");
				ret.append(interchangeObjectToString(ent.getValue()));
				ret.append(", ");
			}
			ret.delete(ret.length()-2,ret.length());
			ret.append(")");
			return ret.toString();
		}
		else if(wat instanceof Object[]) {
			Object[] awat = (Object[])wat;
			if(awat.length == 0) return "empty array";
			StringBuilder ret = new StringBuilder();
			ret.append("array(");
			for(Object o : awat) {
				ret.append(interchangeObjectToString(o));
				ret.append(", ");
			}
			ret.delete(ret.length()-2,ret.length());
			ret.append(")");
			return ret.toString();
		}
		else if(wat instanceof Integer) return String.format("int(%d)", wat);
		else if(wat instanceof Number) return String.format("number(%d/%g)", ((Number)wat).longValue(), ((Number)wat).doubleValue());
		else if(wat instanceof Boolean) return String.format("boolean(%b)", wat);
		/* TODO: NBTTagCompound */
		else return String.format("INCOMPATIBLE(%s)", wat.getClass().getSimpleName());
	}
	
	private void traceInvoke(String who, String func, Object[] params, boolean discard) {
		OCARM.logger.info("%s: %s(%s)%s", who, func, interchangeObjectToString(params), discard?" (discard result)":"");
	}
	private void traceInvokeLimitReached() { OCARM.logger.info("  (reached call limit)"); }
	private void traceInvokeReply(Integer status, Object[] result) {
		OCARM.logger.info("  %d, %s", status, interchangeObjectToString(result));
	}

	public Machine getMachine() {
		return machine;
	}
}
