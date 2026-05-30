/**
 * 채팅 목록 화면
 */
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { useRooms } from '../../lib/useRooms';
import type { RoomResponse } from '../../api/types';

interface RoomItemProps {
  room: RoomResponse;
}

function RoomItem({ room }: RoomItemProps) {
  const displayName = room.name ?? (room.type === 'DIRECT' ? '1:1 채팅' : '그룹 채팅');

  return (
    <Pressable
      style={styles.card}
      onPress={() => router.push(`/rooms/${room.id}`)}
      accessibilityRole="button"
      accessibilityLabel={`${displayName} 채팅방 열기`}
    >
      <Text style={styles.roomName} accessibilityRole="text">
        {displayName}
      </Text>
      <Text style={styles.roomType} accessibilityRole="text">
        {room.type === 'DIRECT' ? '1:1' : '그룹'}
      </Text>
    </Pressable>
  );
}

export default function RoomsListScreen() {
  const { data, isLoading, isError } = useRooms();

  if (isLoading) {
    return (
      <View style={styles.centered} accessibilityLabel="채팅 목록 로딩 중">
        <ActivityIndicator size="large" color="#007AFF" />
      </View>
    );
  }

  if (isError) {
    return (
      <View style={styles.centered} accessibilityLabel="채팅 목록 오류">
        <Text style={styles.errorText} accessibilityRole="alert">
          채팅 목록을 불러오지 못했습니다.
        </Text>
      </View>
    );
  }

  const rooms = data ?? [];

  return (
    <View style={styles.container} accessible={true} accessibilityLabel="채팅 목록 화면">
      <Text style={styles.title}>채팅</Text>
      {rooms.length === 0 ? (
        <Text style={styles.emptyText} accessibilityRole="text">
          채팅방이 없습니다.
        </Text>
      ) : (
        <FlatList
          data={rooms}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => <RoomItem room={item} />}
          contentContainerStyle={styles.list}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingTop: 60,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1C1C1E',
    marginBottom: 20,
  },
  list: {
    paddingBottom: 40,
  },
  card: {
    backgroundColor: '#F2F2F7',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  roomName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1C1C1E',
    flex: 1,
  },
  roomType: {
    fontSize: 12,
    color: '#8E8E93',
    marginLeft: 8,
  },
  emptyText: {
    fontSize: 15,
    color: '#8E8E93',
    textAlign: 'center',
    marginTop: 40,
  },
  errorText: {
    color: '#FF3B30',
    fontSize: 15,
  },
});
