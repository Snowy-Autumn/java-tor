package snowy.autumn.tor.cell;

import snowy.autumn.tor.cell.cells.*;
import snowy.autumn.tor.cell.cells.relay.RelayCell;

import java.nio.ByteBuffer;

public abstract class Cell {

    public @interface ClientDoesNotImplement {};

    public static final int FIXED_CELL_BODY_LENGTH = 509;

    public static final byte VERSIONS = 7;
    public static final byte CERTS = (byte) 129;
    public static final byte AUTH_CHALLENGE = (byte) 130;
    public static final byte NET_INFO = 8;
    public static final byte CREATE_FAST = 5;
    public static final byte CREATED_FAST = 6;
    public static final byte RELAY = 3;
    public static final byte RELAY_EARLY = 9;
    public static final byte DESTROY = 4;

    int circuitId;
    byte command;

    public Cell(int circuitId, byte command) {
        this.circuitId = circuitId;
        this.command = command;
    }

    public static boolean isFixedLengthCell(byte command) {
        return Byte.toUnsignedInt(command) < 128 && Byte.toUnsignedInt(command) != VERSIONS;
    }

    public boolean isFixedLengthCell() {
        return isFixedLengthCell(command);
    }

    protected abstract byte[] serialiseBody();

    public byte[] serialiseCell() {
        byte[] body = serialiseBody();
        boolean fixedLengthCell = isFixedLengthCell();
        // When version < 4 then CIRCID_LEN == 2, but since we only support versions 4, 5 at the moment,
        // then the only time this happens is in the VERSIONS cell.
        int cellSize = 1 + (command == VERSIONS ? 2 : 4) + (fixedLengthCell ? FIXED_CELL_BODY_LENGTH : 2 + body.length);
        ByteBuffer buffer = ByteBuffer.allocate(cellSize);
        if (command == VERSIONS)
            buffer.putShort((short) circuitId);
        else
            buffer.putInt(circuitId);
        buffer.put((byte) command);
        if (!fixedLengthCell)
            buffer.putShort((short) body.length);
        buffer.put(body);
        return buffer.array();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Cell> T parseCell(int circuitId, byte command, byte[] body) {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        switch (command) {
            case VERSIONS -> {
                int[] versions = new int[body.length / 2];
                for (int i = 0; i < versions.length; i++)
                    versions[i] = buffer.getShort();
                return (T) new VersionCell(versions);
            }
            case CERTS -> {
                CertsCell.Cert[] certificates = new CertsCell.Cert[buffer.get()];
                for (int i = 0; i < certificates.length; i++) {
                    byte certType = buffer.get();
                    short certLength = buffer.getShort();
                    byte[] encodedCert = new byte[certLength];
                    buffer.get(encodedCert);
                    certificates[i] = new CertsCell.Cert(certType, certLength, encodedCert);
                }
                return (T) new CertsCell(certificates);
            }
            case AUTH_CHALLENGE -> {
                // Since clients don't care about auth_challenge cells, we can just skip parsing it.
                return (T) new AuthChallengeCell();
                // Since clients don't care about auth_challenge cells, we can just skip parsing it.
            }
            case NET_INFO -> {
                byte[] timestamp = new byte[4];
                buffer.get(timestamp);
                byte initiatorAddressType = buffer.get();
                byte initiatorAddressLength = buffer.get();
                byte[] initiatorAddress = new byte[initiatorAddressLength];
                buffer.get(initiatorAddress);

                NetInfoCell.Address[] senderAddresses = new NetInfoCell.Address[buffer.get()];
                for (int i = 0; i < senderAddresses.length; i++) {
                    byte addressType = buffer.get();
                    byte addressLength = buffer.get();
                    byte[] address = new byte[addressLength];
                    buffer.get(address);
                    senderAddresses[i] = new NetInfoCell.Address(addressType, address);
                }

                return (T) new NetInfoCell(timestamp, new NetInfoCell.Address(initiatorAddressType, initiatorAddress), senderAddresses);
            }
            case CREATED_FAST -> {
                byte[] keyMaterial = new byte[20];
                byte[] KH = new byte[20];
                buffer.get(keyMaterial);
                buffer.get(KH);
                return (T) new CreatedFastCell(circuitId, keyMaterial, KH);
            }
            case RELAY -> {
                return (T) new RelayCell.EncryptedRelayCell(circuitId, body);
            }
            case DESTROY -> {
                return (T) new DestroyCell(circuitId, body[0]);
            }
            default -> throw new Error("Unknown cell received: " + command);
        }
    }

    public int getCircuitId() {
        return circuitId;
    }

    public byte getCommand() {
        return command;
    }

}
